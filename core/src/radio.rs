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
use crate::reducer::{NowPlayingEvent, NowPlayingState, reduce_in_place};
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
    overlay: Mutex<FaveOverlay>,
}

#[derive(Default)]
struct FaveOverlay {
    plus: HashMap<String, Vec<FaveRow>>,
    minus: HashMap<String, Vec<FaveRow>>,
}

struct FaveBusy<'a>(&'a Mutex<bool>);

impl Drop for FaveBusy<'_> {
    fn drop(&mut self) {
        if let Ok(mut g) = self.0.lock() {
            *g = false;
        }
    }
}

fn row_meta(row: &FaveRow) -> String {
    if row.artist.is_empty() {
        row.title.clone()
    } else {
        format!("{} - {}", row.artist, row.title)
    }
}

fn meta_match(a: &str, b: &str) -> bool {
    let key = |s: &str| {
        s.split_whitespace()
            .collect::<Vec<_>>()
            .join(" ")
            .to_lowercase()
    };
    !a.trim().is_empty() && key(a) == key(b)
}

fn row_is_song(row: &FaveRow, track_id: i64, np: &str) -> bool {
    (track_id > 0 && row.tracks_id == track_id) || meta_match(&row_meta(row), np)
}

fn membership_text_has(raw: &str, track_id: i64, np: &str) -> bool {
    raw.lines().any(|l| {
        let Some((id_s, meta)) = l.split_once('\t') else {
            return false;
        };
        let id: i64 = id_s.parse().unwrap_or(0);
        (track_id > 0 && id == track_id) || meta_match(meta, np)
    })
}

fn resolve_tap(
    tap_np: String,
    tap_is_afk: bool,
    tap_track_id: i64,
    snapshot: Option<&Status>,
) -> Option<TapSnapshot> {
    if !tap_np.is_empty() {
        return Some(TapSnapshot {
            is_afk: tap_is_afk,
            track_id: tap_track_id,
            np: tap_np,
        });
    }
    snapshot.map(|s| TapSnapshot {
        is_afk: s.is_afk,
        track_id: s.track_id,
        np: s.np.clone(),
    })
}

const PREF_LIST_NICK: &str = "list_nick";
const PREF_CONN_NICK: &str = "nick";

fn overlay_nicks(cfg: &FaveConfig) -> Vec<String> {
    let mut nicks = Vec::new();
    let list = cfg.list_nick.trim();
    let irc = irc_nick(cfg);
    if !list.is_empty() {
        nicks.push(list.to_string());
    }
    if !irc.is_empty() && !nicks.iter().any(|n| n == irc) {
        nicks.push(irc.to_string());
    }
    nicks
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
                overlay: Mutex::new(FaveOverlay::default()),
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
        reduce_in_place(
            &mut state,
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
            reduce_in_place(&mut state, NowPlayingEvent::Playing(playing));
            state.clone()
        };
        self.fanout(&state);
    }

    /// Icecast is actually playing. Clears stream-down; does not mean a buffer blip.
    pub fn set_player_playing(&self) {
        let state = {
            let mut state = self.inner.state.lock().expect("state");
            reduce_in_place(&mut state, NowPlayingEvent::PlayerPlaying);
            state.clone()
        };
        self.fanout(&state);
    }

    pub fn set_player_error(&self) {
        let state = {
            let mut state = self.inner.state.lock().expect("state");
            reduce_in_place(&mut state, NowPlayingEvent::PlayerError);
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

    /// IME Done on the Favorites nick. Drops the previous nick's disk unless it is still Connection.
    pub fn commit_list_nick(&self, nick: String) -> Result<(), ApiError> {
        self.commit_nick_pref(PREF_LIST_NICK, nick)
    }

    /// Settings → Connection nick. Drops the previous nick's disk unless it is still Favorites.
    pub fn commit_connection_nick(&self, nick: String) -> Result<(), ApiError> {
        self.commit_nick_pref(PREF_CONN_NICK, nick)
    }

    /// `GET /` and read `/assets/{name}/css/`. Not the main thread. Not every poll.
    pub fn sniff_theme(&self) -> Result<Option<String>, ApiError> {
        let bytes = self.inner.http.get(HOME_URL)?;
        let text = String::from_utf8_lossy(&bytes);
        Ok(parse_theme_name(&text))
    }

    /// Theme name from news/schedule/staff HTML already on disk. No GET.
    pub fn cached_theme_name(&self) -> Result<Option<String>, ApiError> {
        for key in ["news:list:1", "schedule", "staff"] {
            if let Some(html) = self.inner.store.get(key)?
                && let Some(name) = parse_theme_name(&html)
            {
                return Ok(Some(name));
            }
        }
        Ok(None)
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
        let mut ram = self.inner.search_ram.lock().expect("search");
        ram.retain(|(query, _), _| query == &q);
        ram.insert((q, page), parsed.clone());
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
        let persist = self.nick_is_committed(&nick);
        self.fetch_faves_inner(nick, page, persist)
    }

    pub fn faves_last_page(&self, nick: String) -> Result<u32, ApiError> {
        let nick = nick.trim().to_string();
        if nick.is_empty() {
            return Ok(1);
        }
        let persist = self.nick_is_committed(&nick);
        self.faves_last_page_inner(nick, persist)
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
        {
            let overlay = self.inner.overlay.lock().expect("overlay");
            if overlay
                .plus
                .get(&nick)
                .into_iter()
                .flatten()
                .any(|r| row_is_song(r, track_id, &np))
            {
                return Ok(true);
            }
            if overlay
                .minus
                .get(&nick)
                .into_iter()
                .flatten()
                .any(|r| row_is_song(r, track_id, &np))
            {
                return Ok(false);
            }
        }
        let Some(raw) = self.inner.store.get(&format!("membership:{nick}"))? else {
            return Ok(false);
        };
        Ok(membership_text_has(&raw, track_id, &np))
    }

    /// GET every `/faves` JSON page for `nick` and replace membership.
    /// Overlay rows stay until the GET agrees.
    pub fn revalidate_membership(&self, nick: String) -> Result<(), ApiError> {
        let nick = nick.trim().to_string();
        if nick.is_empty() || !self.nick_is_committed(&nick) {
            return Ok(());
        }
        let last = self.faves_last_page(nick.clone())?.max(1);
        let mut rows = Vec::new();
        for page in 1..=last {
            rows.extend(self.fetch_faves(nick.clone(), page)?);
        }
        self.remember_membership(nick.clone(), rows.clone())?;
        let mut overlay = self.inner.overlay.lock().expect("overlay");
        if let Some(plus) = overlay.plus.get_mut(&nick) {
            plus.retain(|r| {
                !rows
                    .iter()
                    .any(|g| row_is_song(g, r.tracks_id, &row_meta(r)))
            });
        }
        if let Some(minus) = overlay.minus.get_mut(&nick) {
            minus.retain(|r| {
                rows.iter()
                    .any(|g| row_is_song(g, r.tracks_id, &row_meta(r)))
            });
        }
        Ok(())
    }

    pub fn remember_membership(&self, nick: String, rows: Vec<FaveRow>) -> Result<(), ApiError> {
        let nick = nick.trim().to_string();
        if nick.is_empty() {
            return Ok(());
        }
        let raw = rows
            .iter()
            .map(|r| format!("{}\t{}", r.tracks_id, row_meta(r)))
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
            run_probe(&mut conn, &cfg, &expected)?;
            let fp = conn.server_fingerprint().to_string();
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
    /// `tap_np` nonempty is the snapshot at tap (`docs/spec/requests-faves.md`).
    pub fn add_fave(
        &self,
        cfg: FaveConfig,
        unfave: bool,
        catalog_id: i64,
        tap_np: String,
        tap_is_afk: bool,
        tap_track_id: i64,
    ) -> FaveResult {
        {
            let mut busy = self.inner.fave_busy.lock().expect("fave");
            if *busy {
                return FaveResult::failed("in flight");
            }
            *busy = true;
        }
        let _guard = FaveBusy(&self.inner.fave_busy);
        self.add_fave_inner(cfg, unfave, catalog_id, tap_np, tap_is_afk, tap_track_id)
    }
}

impl RadioCore {
    fn nick_is_committed(&self, nick: &str) -> bool {
        let nick = nick.trim();
        if nick.is_empty() {
            return false;
        }
        let list = self.pref(PREF_LIST_NICK.into()).unwrap_or_default();
        let conn = self.pref(PREF_CONN_NICK.into()).unwrap_or_default();
        nick == list.trim() || nick == conn.trim()
    }

    fn prune_nick_if_unused(&self, old: &str) -> Result<(), ApiError> {
        let old = old.trim();
        if old.is_empty() || self.nick_is_committed(old) {
            return Ok(());
        }
        self.inner.store.delete_nick_disk(old)
    }

    fn commit_nick_pref(&self, key: &str, nick: String) -> Result<(), ApiError> {
        let nick = nick.trim().to_string();
        let old = self.pref(key.to_string())?;
        self.set_pref(key.to_string(), nick)?;
        self.prune_nick_if_unused(&old)
    }

    fn fetch_faves_inner(
        &self,
        nick: String,
        page: u32,
        persist: bool,
    ) -> Result<Vec<FaveRow>, ApiError> {
        let nick = nick.trim().to_string();
        let page = page.max(1);
        if nick.is_empty() {
            return Ok(Vec::new());
        }
        let bytes = self.inner.http.get(&faves_json_url(&nick, page))?;
        let rows = parse_faves_json(&bytes)?;
        if persist {
            let _ = self.inner.store.put_if_changed(
                &format!("faves:{nick}:{page}"),
                &String::from_utf8_lossy(&bytes),
            )?;
        }
        Ok(rows)
    }

    fn faves_last_page_inner(&self, nick: String, persist: bool) -> Result<u32, ApiError> {
        let nick = nick.trim().to_string();
        if nick.is_empty() {
            return Ok(1);
        }
        let bytes = self.inner.http.get(&faves_html_url(&nick))?;
        let html = String::from_utf8_lossy(&bytes);
        let n = faves_html_last_page(&html);
        if persist {
            let _ = self
                .inner
                .store
                .put_if_changed(&format!("faves:{nick}:html_last"), &n.to_string())?;
        }
        Ok(n)
    }

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
        persist: bool,
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
            return self.fetch_faves_inner(nick.to_string(), page, persist);
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
        let persist = self.nick_is_committed(&nick);
        let stored_last = self
            .inner
            .store
            .get(&format!("faves:{nick}:html_last"))?
            .and_then(|s| s.parse().ok());
        let mut last_server = if live && ui_page <= 1 {
            self.faves_last_page_inner(nick.clone(), persist)?
        } else {
            stored_last.unwrap_or(if live {
                self.faves_last_page_inner(nick.clone(), persist)?
            } else {
                1
            })
        }
        .max(1);
        let first = self.faves_json_page(&nick, 1, live, live && ui_page <= 1, persist)?;
        let mut pages = HashMap::new();
        pages.insert(1u32, first.clone());
        if last_server > 1 {
            loop {
                let last_rows = self.faves_json_page(&nick, last_server, live, false, persist)?;
                let prev = self.faves_json_page(&nick, last_server - 1, live, false, persist)?;
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
                let rows = self.faves_json_page(&nick, span.page, live, false, persist)?;
                e.insert(rows);
            }
        }
        let mut rows = take_window(|p| pages.get(&p).cloned().unwrap_or_default(), &spans);
        rows = self.apply_fave_overlay(&nick, ui, rows);
        Ok(FavePage {
            page: ui,
            last_page: last_ui,
            rows,
        })
    }

    fn apply_fave_overlay(&self, nick: &str, ui_page: u32, mut rows: Vec<FaveRow>) -> Vec<FaveRow> {
        let overlay = self.inner.overlay.lock().expect("overlay");
        if let Some(minus) = overlay.minus.get(nick) {
            rows.retain(|r| {
                !minus
                    .iter()
                    .any(|m| row_is_song(r, m.tracks_id, &row_meta(m)))
            });
        }
        if ui_page <= 1
            && let Some(plus) = overlay.plus.get(nick)
        {
            for p in plus.iter().rev() {
                if !rows
                    .iter()
                    .any(|r| row_is_song(r, p.tracks_id, &row_meta(p)))
                {
                    rows.insert(0, p.clone());
                }
            }
        }
        rows
    }

    fn catalog_id_for(&self, nicks: &[String], tap: &TapSnapshot) -> Option<i64> {
        if tap.is_afk && tap.track_id > 0 {
            return Some(tap.track_id);
        }
        for nick in nicks {
            if let Ok(overlay) = self.inner.overlay.lock() {
                let hit = overlay
                    .plus
                    .get(nick)
                    .into_iter()
                    .flatten()
                    .chain(overlay.minus.get(nick).into_iter().flatten())
                    .find(|r| row_is_song(r, 0, &tap.np) && r.tracks_id > 0)
                    .map(|r| r.tracks_id);
                if hit.is_some() {
                    return hit;
                }
            }
            if let Ok(Some(raw)) = self.inner.store.get(&format!("membership:{nick}")) {
                for line in raw.lines() {
                    let Some((id_s, meta)) = line.split_once('\t') else {
                        continue;
                    };
                    let id: i64 = id_s.parse().unwrap_or(0);
                    if id > 0 && meta_match(meta, &tap.np) {
                        return Some(id);
                    }
                }
            }
        }
        None
    }

    fn remember_toggle(
        &self,
        nicks: &[String],
        tap: &TapSnapshot,
        catalog_id: Option<i64>,
        favorited: bool,
    ) {
        let id = catalog_id
            .filter(|i| *i > 0)
            .or(if tap.is_afk && tap.track_id > 0 {
                Some(tap.track_id)
            } else {
                None
            })
            .unwrap_or(0);
        let (artist, title) = crate::parse::split_np(&tap.np);
        let row = FaveRow {
            tracks_id: id,
            artist,
            title,
            lastrequested: 0,
            lastplayed: 0,
            requestcount: 0,
        };
        let mut overlay = self.inner.overlay.lock().expect("overlay");
        for nick in nicks {
            overlay
                .plus
                .entry(nick.clone())
                .or_default()
                .retain(|r| !row_is_song(r, id, &tap.np));
            overlay
                .minus
                .entry(nick.clone())
                .or_default()
                .retain(|r| !row_is_song(r, id, &tap.np));
            if favorited {
                overlay
                    .plus
                    .entry(nick.clone())
                    .or_default()
                    .push(row.clone());
            } else {
                overlay
                    .minus
                    .entry(nick.clone())
                    .or_default()
                    .push(row.clone());
            }
        }
    }

    fn add_fave_inner(
        &self,
        cfg: FaveConfig,
        unfave: bool,
        catalog_id: i64,
        tap_np: String,
        tap_is_afk: bool,
        tap_track_id: i64,
    ) -> FaveResult {
        if nick_is_empty(irc_nick(&cfg)) {
            return FaveResult::noop();
        }
        let tap = match resolve_tap(tap_np, tap_is_afk, tap_track_id, self.snapshot().as_ref()) {
            Some(t) => t,
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
        let nicks = overlay_nicks(&cfg);
        let catalog = if catalog_id > 0 {
            Some(catalog_id)
        } else {
            self.catalog_id_for(&nicks, &tap)
        };
        if unfave && catalog.is_none() {
            return FaveResult::failed("no catalog id");
        }
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
            Ok(r) => {
                if r.kind == crate::irc::FaveKind::Success {
                    self.remember_toggle(&nicks, &tap, catalog, r.favorited);
                }
                r
            }
            Err(e) => FaveResult::failed(e.to_string()),
        }
    }

    fn apply_bytes(&self, bytes: &[u8]) -> Result<Status, ApiError> {
        let status = parse_status(bytes)?;
        let json = String::from_utf8_lossy(bytes).into_owned();
        let state = {
            let mut state = self.inner.state.lock().expect("state");
            reduce_in_place(
                &mut state,
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
        match inner.http.get(API_URL) {
            Ok(bytes) => match parse_status(&bytes) {
                Ok(status) => {
                    let json = String::from_utf8_lossy(&bytes).into_owned();
                    let (down, playing) = {
                        let mut state = inner.state.lock().expect("state");
                        reduce_in_place(
                            &mut state,
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
        let ui = *inner.ui_visible.lock().expect("ui");
        let playing = inner.state.lock().expect("state").playing;
        let fails = *inner.failures.lock().expect("fail");
        thread::sleep(Duration::from_secs(poll_interval(ui, playing, fails)));
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

    #[test]
    fn overlay_fills_before_membership_get() {
        let dir = std::env::temp_dir().join(format!("geiravor-ov-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let core = RadioCore::new(dir.to_str().unwrap().into()).unwrap();
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: "Artist - Title".into(),
        };
        core.remember_toggle(&["Alice".into()], &tap, Some(42), true);
        assert!(
            core.membership_has("Alice".into(), 42, "Artist - Title".into())
                .unwrap()
        );
        core.remember_toggle(&["Alice".into()], &tap, Some(42), false);
        assert!(
            !core
                .membership_has("Alice".into(), 42, "Artist - Title".into())
                .unwrap()
        );
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn tap_np_wins_over_later_snapshot() {
        let json = include_str!("../tests/fixtures/api_snapshot.json");
        let status = parse_status_str(json).unwrap();
        let tap = resolve_tap("Tapped - Song".into(), true, 42, Some(&status)).unwrap();
        assert_eq!(tap.np, "Tapped - Song");
        assert_eq!(tap.track_id, 42);
        assert!(tap.is_afk);
        assert_ne!(tap.np, status.np);
        assert_ne!(tap.track_id, status.track_id);
    }

    #[test]
    fn membership_persists_row_meta_without_empty_artist_dash() {
        let dir = std::env::temp_dir().join(format!("geiravor-mem-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let core = RadioCore::new(dir.to_str().unwrap().into()).unwrap();
        core.remember_membership(
            "Alice".into(),
            vec![FaveRow {
                tracks_id: 7,
                artist: String::new(),
                title: "Solo".into(),
                lastrequested: 0,
                lastplayed: 0,
                requestcount: 0,
            }],
        )
        .unwrap();
        let raw = core.inner.store.get("membership:Alice").unwrap().unwrap();
        assert_eq!(raw, "7\tSolo");
        assert!(
            core.membership_has("Alice".into(), 0, "Solo".into())
                .unwrap()
        );
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn changing_list_nick_drops_old_disk_unless_still_connection() {
        let dir = std::env::temp_dir().join(format!("geiravor-nick-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let core = RadioCore::new(dir.to_str().unwrap().into()).unwrap();
        core.remember_membership(
            "Alice".into(),
            vec![FaveRow {
                tracks_id: 1,
                artist: "A".into(),
                title: "T".into(),
                lastrequested: 0,
                lastplayed: 0,
                requestcount: 0,
            }],
        )
        .unwrap();
        core.set_pref("list_nick".into(), "Alice".into()).unwrap();
        core.commit_list_nick("Bob".into()).unwrap();
        assert!(core.inner.store.get("membership:Alice").unwrap().is_none());
        assert_eq!(core.pref("list_nick".into()).unwrap(), "Bob");

        core.remember_membership(
            "Alice".into(),
            vec![FaveRow {
                tracks_id: 1,
                artist: "A".into(),
                title: "T".into(),
                lastrequested: 0,
                lastplayed: 0,
                requestcount: 0,
            }],
        )
        .unwrap();
        core.set_pref("nick".into(), "Alice".into()).unwrap();
        core.set_pref("list_nick".into(), "Alice".into()).unwrap();
        core.commit_list_nick("Carol".into()).unwrap();
        assert!(core.inner.store.get("membership:Alice").unwrap().is_some());
        let _ = std::fs::remove_dir_all(&dir);
    }
}
