//! Process-wide domain. UniFFI constructor takes `files_dir` from the shell.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use crate::error::ApiError;
use crate::faves::{
    FavePage, FaveRow, faves_html_last_page, faves_html_url, faves_json_url, parse_faves_json,
    trim_faves_overlap,
};
use crate::html::extract_csrf;
use crate::irc::{
    FaveConfig, FaveResult, IrcProfile, RIZON_HOST, RIZON_PORT, TapSnapshot, attach_nick,
    connect_irc, irc_nick, nick_is_empty, run_add_fave, run_probe, with_retries,
};
use crate::net::{Coalescer, HttpClient, ReqwestClient};
use crate::news::{
    NewsArticle, NewsList, news_article_url, news_list_url, parse_news_article, parse_news_list,
};
use crate::parse::{API_URL, HOME_URL, Status, parse_status, parse_status_str, parse_theme_name};
use crate::poll::poll_interval;
use crate::reducer::{NowPlayingEvent, NowPlayingState, reduce};
use crate::schedule::{SCHEDULE_URL, ScheduleDay, parse_schedule};
use crate::search::{
    CAN_REQUEST_URL, RequestResult, SEARCH_HTML_URL, SearchPage, parse_can_request,
    parse_request_body, parse_search, request_url, search_url,
};
use crate::staff::{STAFF_URL, StaffGroup, parse_staff};
use crate::store::Store;
use crate::window::{
    FAVES_SERVER_SIZE, NEWS_SERVER_SIZE, SEARCH_SERVER_SIZE, catalog_total, server_spans,
    take_window, ui_last_page,
};

/// Callbacks from the poller. Arrive off the Android main thread.
#[uniffi::export(callback_interface)]
pub trait StatusListener: Send + Sync {
    fn on_status(&self, status: Status, stream_down: bool, playing: bool);
}

struct Inner<C: HttpClient> {
    store: Store,
    http: Coalescer<C>,
    state: Mutex<NowPlayingState>,
    ui_visible: Mutex<bool>,
    failures: Mutex<u32>,
    listeners: Mutex<Vec<Box<dyn StatusListener>>>,
    poller_started: Mutex<bool>,
    fave_busy: Mutex<bool>,
    search_ram: Mutex<HashMap<(String, u32), SearchPage>>,
}

/// Named UniFFI surface. Kotlin must not call this on the main thread for GET/sqlite.
#[derive(uniffi::Object)]
pub struct RadioCore {
    inner: Arc<Inner<ReqwestClient>>,
}

#[uniffi::export]
impl RadioCore {
    /// Open sqlite under `files_dir` and restore last-paint if present.
    #[uniffi::constructor]
    pub fn new(files_dir: String) -> Result<Arc<Self>, ApiError> {
        let store = Store::open(&files_dir)?;
        let http = Coalescer::new(ReqwestClient::new()?);
        let mut state = NowPlayingState::default();
        if let Some(json) = store.last_paint()?
            && let Ok(status) = parse_status_str(&json)
        {
            state.status = Some(status);
        }
        Ok(Arc::new(Self {
            inner: Arc::new(Inner {
                store,
                http,
                state: Mutex::new(state),
                ui_visible: Mutex::new(false),
                failures: Mutex::new(0),
                listeners: Mutex::new(Vec::new()),
                poller_started: Mutex::new(false),
                fave_busy: Mutex::new(false),
                search_ram: Mutex::new(HashMap::new()),
            }),
        }))
    }

    /// Seed last paint. No-op if a snapshot is already set (does not clobber).
    pub fn restore_snapshot(&self, json: String) -> Result<(), ApiError> {
        let mut state = self.inner.state.lock().expect("state");
        if state.status.is_some() {
            return Ok(());
        }
        let status = parse_status_str(&json)?;
        *state = reduce(
            state.clone(),
            NowPlayingEvent::Snapshot(Box::new(status.clone())),
        );
        drop(state);
        let _ = self.inner.store.put_last_paint(&json, &status);
        Ok(())
    }

    pub fn snapshot(&self) -> Option<Status> {
        self.inner.state.lock().expect("state").status.clone()
    }

    pub fn is_playing(&self) -> bool {
        self.inner.state.lock().expect("state").playing
    }

    pub fn is_stream_down(&self) -> bool {
        self.inner.state.lock().expect("state").stream_down
    }

    pub fn set_playing(&self, playing: bool) {
        let state = {
            let mut state = self.inner.state.lock().expect("state");
            *state = reduce(
                state.clone(),
                if playing {
                    NowPlayingEvent::PlayerPlaying
                } else {
                    NowPlayingEvent::Playing(false)
                },
            );
            state.clone()
        };
        self.fanout(&state);
    }

    pub fn set_player_error(&self) {
        let state = {
            let mut state = self.inner.state.lock().expect("state");
            *state = reduce(state.clone(), NowPlayingEvent::PlayerError);
            state.clone()
        };
        self.fanout(&state);
    }

    pub fn set_ui_visible(&self, visible: bool) {
        *self.inner.ui_visible.lock().expect("ui") = visible;
    }

    pub fn add_listener(&self, listener: Box<dyn StatusListener>) {
        self.inner
            .listeners
            .lock()
            .expect("listeners")
            .push(listener);
    }

    /// GET `/api` once (coalesced). Used by the poller and by fave tap-if-empty.
    pub fn fetch_status(&self) -> Result<Status, ApiError> {
        let bytes = self.inner.http.get(API_URL)?;
        self.apply_bytes(&bytes)
    }

    /// Start the process-wide poller once.
    pub fn start_poller(&self) {
        let mut started = self.inner.poller_started.lock().expect("poller");
        if *started {
            return;
        }
        *started = true;
        let inner = self.inner.clone();
        let _ = thread::Builder::new()
            .name("geiravor-poll".into())
            .spawn(move || poll_loop(inner));
        tracing::info!("poller start");
    }

    /// Non-secret pref. Empty string if missing. Not the main thread.
    pub fn pref(&self, key: String) -> Result<String, ApiError> {
        Ok(self.inner.store.get(&key)?.unwrap_or_default())
    }

    /// Persist a non-secret pref only if the value changed. Not the main thread.
    pub fn set_pref(&self, key: String, value: String) -> Result<(), ApiError> {
        self.inner.store.put_if_changed(&key, &value)?;
        Ok(())
    }

    /// `GET /` and read `/assets/{name}/css/`. Not the main thread. Not every poll.
    pub fn sniff_theme(&self) -> Result<Option<String>, ApiError> {
        let bytes = self.inner.http.get(HOME_URL)?;
        let text = String::from_utf8_lossy(&bytes);
        Ok(parse_theme_name(&text))
    }

    pub fn cached_news_list(&self, page: u32) -> Result<NewsList, ApiError> {
        let page = page.max(1);
        match self.inner.store.get(&format!("news:list:{page}"))? {
            Some(html) if !html.is_empty() => parse_news_list(&html, page),
            _ => Ok(NewsList {
                page,
                last_page: 1,
                cards: Vec::new(),
            }),
        }
    }

    pub fn fetch_news_list(&self, page: u32) -> Result<NewsList, ApiError> {
        let page = page.max(1);
        let bytes = self.inner.http.get(&news_list_url(page))?;
        let html = String::from_utf8_lossy(&bytes).into_owned();
        let _ = self
            .inner
            .store
            .put_if_changed(&format!("news:list:{page}"), &html)?;
        parse_news_list(&html, page)
    }

    /// Disk-only UI window. `fit` is rows that fill the pane.
    pub fn cached_news_window(&self, ui_page: u32, fit: u32) -> Result<NewsList, ApiError> {
        self.news_window_inner(ui_page, fit, false)
    }

    /// UI window over HTML pages. Page 1 re-GETs HTML page 1. Worker-thread only.
    pub fn news_window(&self, ui_page: u32, fit: u32) -> Result<NewsList, ApiError> {
        self.news_window_inner(ui_page, fit, true)
    }

    pub fn cached_news_article(&self, id: i64) -> Result<Option<NewsArticle>, ApiError> {
        match self.inner.store.get(&format!("news:article:{id}"))? {
            Some(html) if !html.is_empty() => Ok(Some(parse_news_article(&html, id)?)),
            _ => Ok(None),
        }
    }

    pub fn fetch_news_article(&self, id: i64) -> Result<NewsArticle, ApiError> {
        let bytes = self.inner.http.get(&news_article_url(id))?;
        let html = String::from_utf8_lossy(&bytes).into_owned();
        let _ = self
            .inner
            .store
            .put_if_changed(&format!("news:article:{id}"), &html)?;
        parse_news_article(&html, id)
    }

    pub fn post_comment(&self, id: i64, body: String) -> Result<NewsArticle, ApiError> {
        let text = body.trim();
        if text.is_empty() || text.len() > 500 {
            return Err(ApiError::Decode {
                detail: "comment empty or too long".into(),
            });
        }
        let token = self.csrf_token()?;
        let url = news_article_url(id);
        let form = [("comment", text)];
        let (code, bytes) = self.inner.http.post_csrf_raw(&url, &token, &form)?;
        if code == 403 {
            let token = self.csrf_token()?;
            let (code2, bytes2) = self.inner.http.post_csrf_raw(&url, &token, &form)?;
            if !(200..300).contains(&code2) {
                return Err(ApiError::Http { code: code2 });
            }
            let html = String::from_utf8_lossy(&bytes2).into_owned();
            let _ = self
                .inner
                .store
                .put_if_changed(&format!("news:article:{id}"), &html)?;
            return parse_news_article(&html, id);
        }
        if !(200..300).contains(&code) {
            return Err(ApiError::Http { code });
        }
        let html = String::from_utf8_lossy(&bytes).into_owned();
        let _ = self
            .inner
            .store
            .put_if_changed(&format!("news:article:{id}"), &html)?;
        parse_news_article(&html, id)
    }

    pub fn cached_schedule(&self) -> Result<Vec<ScheduleDay>, ApiError> {
        match self.inner.store.get("schedule")? {
            Some(html) if !html.is_empty() => parse_schedule(&html),
            _ => Ok(Vec::new()),
        }
    }

    pub fn fetch_schedule(&self) -> Result<Vec<ScheduleDay>, ApiError> {
        let bytes = self.inner.http.get(SCHEDULE_URL)?;
        let html = String::from_utf8_lossy(&bytes).into_owned();
        let _ = self.inner.store.put_if_changed("schedule", &html)?;
        parse_schedule(&html)
    }

    pub fn cached_staff(&self) -> Result<Vec<StaffGroup>, ApiError> {
        match self.inner.store.get("staff")? {
            Some(html) if !html.is_empty() => parse_staff(&html),
            _ => Ok(Vec::new()),
        }
    }

    pub fn fetch_staff(&self) -> Result<Vec<StaffGroup>, ApiError> {
        let bytes = self.inner.http.get(STAFF_URL)?;
        let html = String::from_utf8_lossy(&bytes).into_owned();
        let _ = self.inner.store.put_if_changed("staff", &html)?;
        parse_staff(&html)
    }

    pub fn search(&self, query: String, page: u32) -> Result<SearchPage, ApiError> {
        let q = query.trim().to_string();
        let page = page.max(1);
        if q.is_empty() {
            return Ok(SearchPage {
                total: 0,
                per_page: 20,
                current_page: 1,
                last_page: 1,
                tracks: Vec::new(),
            });
        }
        if page != 1 {
            let ram = self.inner.search_ram.lock().expect("search");
            if let Some(hit) = ram.get(&(q.clone(), page)) {
                return Ok(hit.clone());
            }
        }
        let bytes = self.inner.http.get(&search_url(&q, page))?;
        let parsed = parse_search(&bytes)?;
        if parsed.total == 0 && parsed.tracks.is_empty() {
            return Ok(parsed);
        }
        self.inner
            .search_ram
            .lock()
            .expect("search")
            .insert((q, page), parsed.clone());
        Ok(parsed)
    }

    /// UI window over search JSON pages. Worker-thread only.
    pub fn search_window(
        &self,
        query: String,
        ui_page: u32,
        fit: u32,
    ) -> Result<SearchPage, ApiError> {
        let q = query.trim().to_string();
        let fit = fit.max(1);
        if q.is_empty() {
            return Ok(SearchPage {
                total: 0,
                per_page: SEARCH_SERVER_SIZE,
                current_page: 1,
                last_page: 1,
                tracks: Vec::new(),
            });
        }
        let force_first = ui_page <= 1;
        let first = self.search_server(&q, 1, force_first)?;
        if first.total == 0 && first.tracks.is_empty() {
            return Ok(SearchPage {
                total: 0,
                per_page: SEARCH_SERVER_SIZE,
                current_page: 1,
                last_page: 1,
                tracks: Vec::new(),
            });
        }
        let total = first.total;
        let last_server = first.last_page.max(1);
        let server_size = first.per_page.max(1);
        let last_ui = ui_last_page(total, fit);
        let ui = ui_page.max(1).min(last_ui);
        let spans: Vec<_> = server_spans(ui, fit, total, server_size)
            .into_iter()
            .filter(|s| s.page <= last_server)
            .collect();
        let mut pages = HashMap::new();
        pages.insert(1u32, first.tracks.clone());
        for span in &spans {
            if let std::collections::hash_map::Entry::Vacant(e) = pages.entry(span.page) {
                let p = self.search_server(&q, span.page, false)?;
                e.insert(p.tracks);
            }
        }
        let tracks = take_window(|p| pages.get(&p).cloned().unwrap_or_default(), &spans);
        Ok(SearchPage {
            total,
            per_page: first.per_page,
            current_page: ui,
            last_page: last_ui,
            tracks,
        })
    }

    pub fn can_request(&self) -> Result<bool, ApiError> {
        let bytes = self.inner.http.get(CAN_REQUEST_URL)?;
        parse_can_request(&bytes)
    }

    pub fn request_track(&self, id: i64) -> Result<RequestResult, ApiError> {
        if id <= 0 {
            return Ok(RequestResult {
                ok: false,
                text: "unknown id".into(),
            });
        }
        let url = request_url(id);
        let token = self.csrf_token()?;
        let (code, body) = self.inner.http.post_csrf_raw(&url, &token, &[])?;
        if code == 403 {
            let token = self.csrf_token()?;
            let (code2, body2) = self.inner.http.post_csrf_raw(&url, &token, &[])?;
            if !(200..300).contains(&code2) {
                return Err(ApiError::Http { code: code2 });
            }
            return parse_request_body(&body2);
        }
        if !(200..300).contains(&code) {
            return Err(ApiError::Http { code });
        }
        parse_request_body(&body)
    }

    pub fn cached_faves(&self, nick: String, page: u32) -> Result<Vec<FaveRow>, ApiError> {
        let nick = nick.trim().to_string();
        let page = page.max(1);
        if nick.is_empty() {
            return Ok(Vec::new());
        }
        match self.inner.store.get(&format!("faves:{nick}:{page}"))? {
            Some(json) if !json.is_empty() => parse_faves_json(json.as_bytes()),
            _ => Ok(Vec::new()),
        }
    }

    pub fn fetch_faves(&self, nick: String, page: u32) -> Result<Vec<FaveRow>, ApiError> {
        let nick = nick.trim().to_string();
        let page = page.max(1);
        if nick.is_empty() {
            return Ok(Vec::new());
        }
        let bytes = self.inner.http.get(&faves_json_url(&nick, page))?;
        let rows = parse_faves_json(&bytes)?;
        let _ = self.inner.store.put_if_changed(
            &format!("faves:{nick}:{page}"),
            &String::from_utf8_lossy(&bytes),
        )?;
        Ok(rows)
    }

    pub fn faves_last_page(&self, nick: String) -> Result<u32, ApiError> {
        let nick = nick.trim().to_string();
        if nick.is_empty() {
            return Ok(1);
        }
        let bytes = self.inner.http.get(&faves_html_url(&nick))?;
        let html = String::from_utf8_lossy(&bytes);
        let n = faves_html_last_page(&html);
        let _ = self
            .inner
            .store
            .put_if_changed(&format!("faves:{nick}:html_last"), &n.to_string())?;
        Ok(n)
    }

    /// Disk-only favorites UI window.
    pub fn cached_faves_window(
        &self,
        nick: String,
        ui_page: u32,
        fit: u32,
    ) -> Result<FavePage, ApiError> {
        self.faves_window_inner(nick, ui_page, fit, false)
    }

    /// UI window over faves JSON pages. Worker-thread only.
    pub fn faves_window(&self, nick: String, ui_page: u32, fit: u32) -> Result<FavePage, ApiError> {
        self.faves_window_inner(nick, ui_page, fit, true)
    }

    pub fn trim_fave_page(&self, prev: Vec<FaveRow>, last: Vec<FaveRow>) -> Vec<FaveRow> {
        trim_faves_overlap(&prev, last)
    }

    pub fn membership_has(
        &self,
        nick: String,
        track_id: i64,
        np: String,
    ) -> Result<bool, ApiError> {
        let nick = nick.trim().to_string();
        if nick.is_empty() {
            return Ok(false);
        }
        let Some(raw) = self.inner.store.get(&format!("membership:{nick}"))? else {
            return Ok(false);
        };
        if track_id > 0 && raw.lines().any(|l| l.starts_with(&format!("{track_id}\t"))) {
            return Ok(true);
        }
        if !np.trim().is_empty() {
            return Ok(raw
                .lines()
                .any(|l| l.ends_with(&format!("\t{}", np.trim()))));
        }
        Ok(false)
    }

    pub fn remember_membership(&self, nick: String, rows: Vec<FaveRow>) -> Result<(), ApiError> {
        let nick = nick.trim().to_string();
        if nick.is_empty() {
            return Ok(());
        }
        let raw = rows
            .iter()
            .map(|r| format!("{}\t{} - {}", r.tracks_id, r.artist, r.title))
            .collect::<Vec<_>>()
            .join("\n");
        self.inner
            .store
            .put_if_changed(&format!("membership:{nick}"), &raw)?;
        Ok(())
    }

    /// Handshake only. Returns `SHA-256 …` of the server cert. Worker-thread only.
    pub fn probe(&self, cfg: FaveConfig) -> Result<String, ApiError> {
        if nick_is_empty(irc_nick(&cfg)) {
            return Err(ApiError::Decode {
                detail: "empty nick".into(),
            });
        }
        let (host, port, insecure) = match cfg.profile {
            IrcProfile::Rizon => (RIZON_HOST.to_string(), RIZON_PORT, false),
            IrcProfile::Bouncer => {
                let host = cfg.bouncer_host.clone();
                let port = if cfg.bouncer_port == 0 {
                    crate::irc::DEFAULT_BOUNCER_PORT
                } else {
                    cfg.bouncer_port
                };
                (host, port, cfg.allow_insecure_tls)
            }
        };
        let expected = match cfg.profile {
            IrcProfile::Rizon => irc_nick(&cfg).to_string(),
            IrcProfile::Bouncer => attach_nick(),
        };
        let fp = with_retries(|| {
            let mut conn = connect_irc(
                &host,
                port,
                insecure,
                &cfg.client_cert_pem,
                &cfg.client_key_pem,
                &cfg.tls_fingerprint,
            )?;
            let fp = run_probe(&mut conn, &cfg, &expected)?;
            if matches!(cfg.profile, IrcProfile::Rizon) {
                let _ = conn.write_line("QUIT :Geiravor");
            }
            let _ = conn.close_notify();
            Ok(fp)
        })
        .map_err(|e| ApiError::Network {
            detail: e.to_string(),
        })?;
        Ok(format!("SHA-256 {fp}"))
    }

    /// IRC add/remove fave. Empty nick is a no-op. Worker-thread only.
    pub fn add_fave(&self, cfg: FaveConfig, unfave: bool, catalog_id: i64) -> FaveResult {
        {
            let mut busy = self.inner.fave_busy.lock().expect("fave");
            if *busy {
                return FaveResult::failed("in flight");
            }
            *busy = true;
        }
        let result = self.add_fave_inner(cfg, unfave, catalog_id);
        *self.inner.fave_busy.lock().expect("fave") = false;
        result
    }
}

impl RadioCore {
    fn csrf_token(&self) -> Result<String, ApiError> {
        let bytes = self.inner.http.get(SEARCH_HTML_URL)?;
        let html = String::from_utf8_lossy(&bytes);
        extract_csrf(&html).ok_or(ApiError::Decode {
            detail: "csrf token missing".into(),
        })
    }

    fn news_ids(list: &NewsList) -> Vec<i64> {
        list.cards.iter().map(|c| c.id).collect()
    }

    fn news_html_page(&self, page: u32, live: bool, force_get: bool) -> Result<NewsList, ApiError> {
        if !force_get
            && self
                .inner
                .store
                .get(&format!("news:list:{page}"))?
                .is_some()
        {
            return self.cached_news_list(page);
        }
        if live {
            return self.fetch_news_list(page);
        }
        self.cached_news_list(page)
    }

    fn news_window_inner(&self, ui_page: u32, fit: u32, live: bool) -> Result<NewsList, ApiError> {
        let fit = fit.max(1);
        let old = self.cached_news_list(1)?;
        let first = self.news_html_page(1, live, live && ui_page <= 1)?;
        if live
            && ui_page <= 1
            && !old.cards.is_empty()
            && Self::news_ids(&old) != Self::news_ids(&first)
        {
            let html1 = self.inner.store.get("news:list:1")?;
            self.inner.store.delete_like("news:list:%")?;
            if let Some(html) = html1 {
                let _ = self.inner.store.put_if_changed("news:list:1", &html)?;
            }
        }
        let mut html_last = first.last_page.max(1);
        let mut last_list = if html_last == 1 {
            first.clone()
        } else {
            self.news_html_page(html_last, live, false)?
        };
        if last_list.cards.is_empty() && html_last > 1 {
            html_last = last_list.last_page.max(1);
            last_list = if html_last == 1 {
                first.clone()
            } else {
                self.news_html_page(html_last, live, false)?
            };
        }
        let total = catalog_total(html_last, last_list.cards.len() as u32, NEWS_SERVER_SIZE);
        let last_ui = ui_last_page(total, fit);
        let ui = ui_page.max(1).min(last_ui);
        let spans = server_spans(ui, fit, total, NEWS_SERVER_SIZE);
        let mut pages = HashMap::new();
        pages.insert(1u32, first.cards.clone());
        pages.insert(html_last, last_list.cards.clone());
        for span in &spans {
            if let std::collections::hash_map::Entry::Vacant(e) = pages.entry(span.page) {
                let list = self.news_html_page(span.page, live, false)?;
                e.insert(list.cards);
            }
        }
        let cards = take_window(|p| pages.get(&p).cloned().unwrap_or_default(), &spans);
        Ok(NewsList {
            page: ui,
            last_page: last_ui,
            cards,
        })
    }

    fn faves_json_page(
        &self,
        nick: &str,
        page: u32,
        live: bool,
        force_get: bool,
    ) -> Result<Vec<FaveRow>, ApiError> {
        if !force_get
            && self
                .inner
                .store
                .get(&format!("faves:{nick}:{page}"))?
                .is_some()
        {
            return self.cached_faves(nick.to_string(), page);
        }
        if live {
            return self.fetch_faves(nick.to_string(), page);
        }
        self.cached_faves(nick.to_string(), page)
    }

    fn search_server(&self, query: &str, page: u32, force: bool) -> Result<SearchPage, ApiError> {
        if !force {
            let ram = self.inner.search_ram.lock().expect("search");
            if let Some(hit) = ram.get(&(query.to_string(), page)) {
                return Ok(hit.clone());
            }
        }
        self.search(query.to_string(), page)
    }

    fn faves_window_inner(
        &self,
        nick: String,
        ui_page: u32,
        fit: u32,
        live: bool,
    ) -> Result<FavePage, ApiError> {
        let nick = nick.trim().to_string();
        let fit = fit.max(1);
        if nick.is_empty() {
            return Ok(FavePage {
                page: 1,
                last_page: 1,
                rows: Vec::new(),
            });
        }
        let stored_last = self
            .inner
            .store
            .get(&format!("faves:{nick}:html_last"))?
            .and_then(|s| s.parse().ok());
        let mut last_server = if live && ui_page <= 1 {
            self.faves_last_page(nick.clone())?
        } else {
            stored_last.unwrap_or(if live {
                self.faves_last_page(nick.clone())?
            } else {
                1
            })
        }
        .max(1);
        let first = self.faves_json_page(&nick, 1, live, live && ui_page <= 1)?;
        let mut pages = HashMap::new();
        pages.insert(1u32, first.clone());
        if last_server > 1 {
            loop {
                let last_rows = self.faves_json_page(&nick, last_server, live, false)?;
                let prev = self.faves_json_page(&nick, last_server - 1, live, false)?;
                let trimmed = trim_faves_overlap(&prev, last_rows);
                if !trimmed.is_empty() || last_server == 1 || !live {
                    pages.insert(last_server, trimmed);
                    break;
                }
                last_server -= 1;
                if last_server == 1 {
                    pages.insert(1, first.clone());
                    break;
                }
            }
        }
        let last_len = pages.get(&last_server).map(|r| r.len() as u32).unwrap_or(0);
        let total = catalog_total(last_server, last_len, FAVES_SERVER_SIZE);
        let last_ui = ui_last_page(total, fit);
        let ui = ui_page.max(1).min(last_ui);
        let spans = server_spans(ui, fit, total, FAVES_SERVER_SIZE);
        for span in &spans {
            if let std::collections::hash_map::Entry::Vacant(e) = pages.entry(span.page) {
                let rows = self.faves_json_page(&nick, span.page, live, false)?;
                e.insert(rows);
            }
        }
        let rows = take_window(|p| pages.get(&p).cloned().unwrap_or_default(), &spans);
        Ok(FavePage {
            page: ui,
            last_page: last_ui,
            rows,
        })
    }

    fn add_fave_inner(&self, cfg: FaveConfig, unfave: bool, catalog_id: i64) -> FaveResult {
        if nick_is_empty(irc_nick(&cfg)) {
            return FaveResult::noop();
        }
        let tap = match self.snapshot() {
            Some(s) => TapSnapshot {
                is_afk: s.is_afk,
                track_id: s.track_id,
                np: s.np,
            },
            None => match self.fetch_status() {
                Ok(s) => TapSnapshot {
                    is_afk: s.is_afk,
                    track_id: s.track_id,
                    np: s.np,
                },
                Err(e) => return FaveResult::failed(e.to_string()),
            },
        };
        let (host, port, insecure) = match cfg.profile {
            IrcProfile::Rizon => (RIZON_HOST.to_string(), RIZON_PORT, false),
            IrcProfile::Bouncer => {
                let host = cfg.bouncer_host.clone();
                let port = if cfg.bouncer_port == 0 {
                    crate::irc::DEFAULT_BOUNCER_PORT
                } else {
                    cfg.bouncer_port
                };
                (host, port, cfg.allow_insecure_tls)
            }
        };
        let expected = match cfg.profile {
            IrcProfile::Rizon => irc_nick(&cfg).to_string(),
            IrcProfile::Bouncer => attach_nick(),
        };
        let catalog = if catalog_id > 0 {
            Some(catalog_id)
        } else {
            None
        };
        let latest = || {
            self.snapshot()
                .map(|s| s.np)
                .unwrap_or_else(|| tap.np.clone())
        };
        let outcome = with_retries(|| {
            let mut conn = connect_irc(
                &host,
                port,
                insecure,
                &cfg.client_cert_pem,
                &cfg.client_key_pem,
                &cfg.tls_fingerprint,
            )?;
            let r = run_add_fave(&mut conn, &cfg, &tap, latest, &expected, unfave, catalog)?;
            if matches!(cfg.profile, IrcProfile::Rizon) {
                let _ = conn.write_line("QUIT :Geiravor");
            }
            let _ = conn.close_notify();
            Ok(r)
        });
        match outcome {
            Ok(r) => r,
            Err(e) => FaveResult::failed(e.to_string()),
        }
    }

    fn apply_bytes(&self, bytes: &[u8]) -> Result<Status, ApiError> {
        let status = parse_status(bytes)?;
        let json = String::from_utf8_lossy(bytes).into_owned();
        let state = {
            let mut state = self.inner.state.lock().expect("state");
            *state = reduce(
                state.clone(),
                NowPlayingEvent::Snapshot(Box::new(status.clone())),
            );
            state.clone()
        };
        let _ = self.inner.store.put_last_paint(&json, &status);
        *self.inner.failures.lock().expect("fail") = 0;
        self.fanout(&state);
        Ok(status)
    }

    fn fanout(&self, state: &NowPlayingState) {
        let Some(status) = state.status.clone() else {
            return;
        };
        let listeners = self.inner.listeners.lock().expect("listeners");
        for l in listeners.iter() {
            l.on_status(status.clone(), state.stream_down, state.playing);
        }
    }
}

fn poll_loop(inner: Arc<Inner<ReqwestClient>>) {
    loop {
        let ui = *inner.ui_visible.lock().expect("ui");
        let playing = inner.state.lock().expect("state").playing;
        let fails = *inner.failures.lock().expect("fail");
        thread::sleep(Duration::from_secs(poll_interval(ui, playing, fails)));
        match inner.http.get(API_URL) {
            Ok(bytes) => match parse_status(&bytes) {
                Ok(status) => {
                    let json = String::from_utf8_lossy(&bytes).into_owned();
                    let (down, playing) = {
                        let mut state = inner.state.lock().expect("state");
                        *state = reduce(
                            state.clone(),
                            NowPlayingEvent::Snapshot(Box::new(status.clone())),
                        );
                        (state.stream_down, state.playing)
                    };
                    let _ = inner.store.put_last_paint(&json, &status);
                    *inner.failures.lock().expect("fail") = 0;
                    let listeners = inner.listeners.lock().expect("listeners");
                    for l in listeners.iter() {
                        l.on_status(status.clone(), down, playing);
                    }
                }
                Err(e) => {
                    tracing::warn!(?e, "poll decode");
                    *inner.failures.lock().expect("fail") += 1;
                }
            },
            Err(e) => {
                tracing::warn!(?e, "poll network");
                *inner.failures.lock().expect("fail") += 1;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn restore_does_not_clobber() {
        let dir = std::env::temp_dir().join(format!("geiravor-radio-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let core = RadioCore::new(dir.to_str().unwrap().into()).unwrap();
        let json = include_str!("../tests/fixtures/api_snapshot.json");
        core.restore_snapshot(json.into()).unwrap();
        let first = core.snapshot().unwrap().np;
        let live = include_str!("../tests/fixtures/api_live_dj.json");
        core.restore_snapshot(live.into()).unwrap();
        assert_eq!(core.snapshot().unwrap().np, first);
        core.set_pref("gain".into(), "0.5".into()).unwrap();
        assert_eq!(core.pref("gain".into()).unwrap(), "0.5");
        core.set_pref("gain".into(), "0.5".into()).unwrap();
        assert_eq!(core.pref("gain".into()).unwrap(), "0.5");
        let _ = std::fs::remove_dir_all(&dir);
    }
}
