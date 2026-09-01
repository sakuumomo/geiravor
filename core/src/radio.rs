use std::collections::{HashMap, HashSet};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::csrf::{CSRF_BOOTSTRAP_URL, extract_csrf_token, post_form_csrf, post_with_csrf};
use crate::favorites::{
    FAVES_PER_PAGE, FavoriteRow, FavoritesPage, discover_last_page, faves_html_url, faves_url,
    leftover_last_page, parse_faves, parse_faves_last_page, pick_requestable_id, row_is_song,
    PageSample,
};
use crate::http::{ApiError, blocking_client};
use crate::news::{
    COMMENT_MAX, NewsArticle, NewsAuthor, NewsComment, NewsPage, news_entry_url, news_list_url,
    parse_news_comments, parse_news_entry_body, parse_news_list,
};
use crate::np::split_np;
use crate::irc::{
    DEFAULT_BOUNCER_PORT, FaveConfig, FaveKind, FaveResult, IrcError, IrcProfile, RIZON_HOST,
    RIZON_PORT, TapSnapshot, TlsIrc, attach_nick, connect_irc, connected_message, nick_is_empty,
    run_add_fave, run_probe,
};
use crate::poll::poll_interval;
use crate::progress::{SongProgress, song_progress};
use crate::reducer::{NowPlayingEvent, NowPlayingState};
use crate::search::{
    CAN_REQUEST_URL, RequestResult, SearchPage, parse_can_request, parse_request_result,
    parse_search, request_url, search_url,
};
use crate::schedule::{SCHEDULE_URL, ScheduleDay, parse_schedule};
use crate::staff::{STAFF_URL, StaffMember, parse_staff};
use crate::html::parse_theme_name;
use crate::status::{API_URL, Status, parse_status};

const SITE_URL: &str = "https://r-a-d.io/";

/// HTTP transport for `/api`, search, request, favorites, news, schedule, and staff.
pub trait ApiClient: Send + Sync {
    fn get(&self, url: &str) -> Result<String, ApiError>;
    fn post_csrf(&self, url: &str, token: &str) -> Result<String, ApiError>;
    fn post_form(
        &self,
        url: &str,
        token: &str,
        fields: Vec<(String, String)>,
    ) -> Result<String, ApiError> {
        let _ = (url, token, fields);
        Err(ApiError::Network {
            detail: "no post".into(),
        })
    }
}

struct ReqwestApiClient {
    client: reqwest::blocking::Client,
}

impl ReqwestApiClient {
    fn new() -> Result<Self, ApiError> {
        Ok(Self {
            client: blocking_client()?,
        })
    }
}

impl ApiClient for ReqwestApiClient {
    fn get(&self, url: &str) -> Result<String, ApiError> {
        let response = self
            .client
            .get(url)
            .send()
            .and_then(|r| r.error_for_status())
            .map_err(ApiError::from_reqwest)?;
        response.text().map_err(ApiError::from_reqwest)
    }

    fn post_csrf(&self, url: &str, token: &str) -> Result<String, ApiError> {
        post_with_csrf(&self.client, url, token)
    }

    fn post_form(
        &self,
        url: &str,
        token: &str,
        fields: Vec<(String, String)>,
    ) -> Result<String, ApiError> {
        let owned: Vec<(&str, &str)> = fields.iter().map(|(k, v)| (k.as_str(), v.as_str())).collect();
        post_form_csrf(&self.client, url, token, &owned)
    }
}

#[uniffi::export(callback_interface)]
pub trait StatusListener: Send + Sync {
    fn on_update(&self, status: Status, stream_down: bool);
}

#[derive(Default)]
struct HttpCache {
    membership: HashMap<String, Vec<FavoriteRow>>,
    fave_plus: HashMap<String, Vec<FavoriteRow>>,
    fave_minus: HashMap<String, Vec<FavoriteRow>>,
    news_html: Option<(i64, String)>,
}

#[derive(uniffi::Object)]
pub struct RadioCore {
    client: Arc<dyn ApiClient>,
    state: Mutex<NowPlayingState>,
    listener: Mutex<Option<Arc<dyn StatusListener>>>,
    http_cache: Mutex<HttpCache>,
    search_fetch: Mutex<()>,
    faves_fetch: Mutex<()>,
    news_fetch: Mutex<()>,
    staff_fetch: Mutex<()>,
    schedule_fetch: Mutex<()>,
    theme_name: Mutex<Option<String>>,
    ui_visible: AtomicBool,
    playing: AtomicBool,
    failures: AtomicU32,
    stop: Arc<AtomicBool>,
    thread: Mutex<Option<JoinHandle<()>>>,
    fave_lock: Mutex<()>,
}

impl RadioCore {
    pub fn with_client(client: Arc<dyn ApiClient>) -> Arc<Self> {
        Arc::new(Self {
            client,
            state: Mutex::new(NowPlayingState::default()),
            listener: Mutex::new(None),
            http_cache: Mutex::new(HttpCache::default()),
            search_fetch: Mutex::new(()),
            faves_fetch: Mutex::new(()),
            news_fetch: Mutex::new(()),
            staff_fetch: Mutex::new(()),
            schedule_fetch: Mutex::new(()),
            theme_name: Mutex::new(None),
            ui_visible: AtomicBool::new(false),
            playing: AtomicBool::new(false),
            failures: AtomicU32::new(0),
            stop: Arc::new(AtomicBool::new(false)),
            thread: Mutex::new(None),
            fave_lock: Mutex::new(()),
        })
    }

    pub fn set_listener(&self, listener: Arc<dyn StatusListener>) {
        *self.listener.lock().expect("listener") = Some(listener);
    }

    pub fn tick(&self, now: i64) -> Result<(), ApiError> {
        self.fetch_and_apply(|| now)
    }

    fn poll_once(&self) -> Result<(), ApiError> {
        self.fetch_and_apply(Self::unix_now)
    }

    fn fetch_and_apply(&self, fetched_at: impl FnOnce() -> i64) -> Result<(), ApiError> {
        let body = match self.client.get(API_URL) {
            Ok(body) => body,
            Err(e) => {
                self.failures.fetch_add(1, Ordering::Relaxed);
                return Err(e);
            }
        };
        let status = match parse_status(&body) {
            Ok(status) => status,
            Err(e) => {
                self.failures.fetch_add(1, Ordering::Relaxed);
                return Err(ApiError::from(e));
            }
        };
        let now = fetched_at();
        let mut state = self.state.lock().expect("state");
        state.apply(NowPlayingEvent::Snapshot(status.clone()), now);
        self.failures.store(0, Ordering::Relaxed);
        let down = state.stream_down;
        drop(state);
        self.notify(status, down);
        Ok(())
    }

    pub fn poll_delay(&self) -> Duration {
        poll_interval(
            self.ui_visible.load(Ordering::Relaxed),
            self.playing.load(Ordering::Relaxed),
            self.failures.load(Ordering::Relaxed),
        )
    }

    pub fn take_refetch(&self) -> bool {
        let mut state = self.state.lock().expect("state");
        let r = state.refetch;
        state.refetch = false;
        r
    }

    fn notify(&self, status: Status, stream_down: bool) {
        if let Some(listener) = self.listener.lock().expect("listener").clone() {
            listener.on_update(status, stream_down);
        }
    }

    fn apply_stream_flag(&self, event: NowPlayingEvent) {
        let mut state = self.state.lock().expect("state");
        state.apply(event, Self::unix_now());
        let down = state.stream_down;
        let status = state.status.clone();
        drop(state);
        if let Some(status) = status {
            self.notify(status, down);
        }
    }

    fn unix_now() -> i64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0)
    }

    fn faves_rows(&self, nick: &str, page: i32) -> Result<Vec<FavoriteRow>, ApiError> {
        let _fetch = self.faves_fetch.lock().expect("faves_fetch");
        parse_faves(&self.client.get(&faves_url(nick, page))?)
    }

    fn resolve_faves_last(
        &self,
        nick: &str,
        page: i32,
        data: &[FavoriteRow],
    ) -> Result<i32, ApiError> {
        if (data.len() as i32) < FAVES_PER_PAGE {
            return Ok(page);
        }
        let html_last = self
            .client
            .get(&faves_html_url(nick))
            .map(|html| parse_faves_last_page(&html))
            .unwrap_or(1);
        if html_last > 1 {
            return Ok(html_last);
        }
        Ok(discover_last_page(page.max(1), |probe| {
            self.faves_rows(nick, probe)
                .map(|rows| PageSample::from_rows(&rows))
                .unwrap_or_else(|_| PageSample::from_parts(0, ""))
        }))
    }

    fn trim_faves_last(
        &self,
        nick: &str,
        page: i32,
        last_page: i32,
        data: Vec<FavoriteRow>,
    ) -> Result<(i32, Vec<FavoriteRow>), ApiError> {
        if page != last_page || last_page <= 1 || (data.len() as i32) < FAVES_PER_PAGE {
            return Ok((last_page, data));
        }
        let previous = self.faves_rows(nick, last_page - 1)?;
        let leftover = leftover_last_page(&previous, data);
        if leftover.is_empty() {
            Ok((last_page - 1, leftover))
        } else {
            Ok((last_page, leftover))
        }
    }

    pub(crate) fn catalog_id_for(&self, nick: &str, tap: &TapSnapshot) -> Option<i64> {
        if tap.is_afk && tap.track_id > 0 {
            return Some(tap.track_id);
        }
        let lookup = lookup_track_id(tap);
        let cache = self.http_cache.lock().expect("cache");
        cache
            .fave_plus
            .get(nick)
            .into_iter()
            .flatten()
            .chain(cache.membership.get(nick).into_iter().flatten())
            .find(|row| row_is_song(row, lookup, &tap.np))
            .and_then(|row| row.tracks_id)
            .filter(|id| *id > 0)
    }

    pub(crate) fn remember_toggle(
        &self,
        nick: &str,
        tap: &TapSnapshot,
        catalog_id: Option<i64>,
        favorited: bool,
    ) {
        let id = overlay_track_id(tap, catalog_id);
        let lookup = id.unwrap_or_else(|| lookup_track_id(tap));
        let (artist, title) = split_np(&tap.np);
        let row = FavoriteRow {
            tracks_id: id,
            meta: tap.np.clone(),
            artist,
            title,
            last_requested: None,
            last_played: None,
            request_count: None,
        };
        let mut cache = self.http_cache.lock().expect("cache");
        if favorited {
            let minus = cache.fave_minus.entry(nick.to_string()).or_default();
            minus.retain(|r| !row_is_song(r, lookup, &tap.np));
            let plus = cache.fave_plus.entry(nick.to_string()).or_default();
            plus.retain(|r| !row_is_song(r, lookup, &tap.np));
            plus.push(row);
        } else {
            let plus = cache.fave_plus.entry(nick.to_string()).or_default();
            plus.retain(|r| !row_is_song(r, lookup, &tap.np));
            let minus = cache.fave_minus.entry(nick.to_string()).or_default();
            minus.retain(|r| !row_is_song(r, lookup, &tap.np));
            minus.push(row);
        }
    }

    fn apply_fave_overlay(&self, nick: &str, page: i32, mut data: Vec<FavoriteRow>) -> Vec<FavoriteRow> {
        let cache = self.http_cache.lock().expect("cache");
        if let Some(minus) = cache.fave_minus.get(nick) {
            data.retain(|row| {
                !minus
                    .iter()
                    .any(|m| row_is_song(m, row.tracks_id.unwrap_or(0), &row.meta))
            });
        }
        if page == 1 {
            if let Some(plus) = cache.fave_plus.get(nick) {
                for extra in plus.iter().rev() {
                    if !data
                        .iter()
                        .any(|row| row_is_song(extra, row.tracks_id.unwrap_or(0), &row.meta))
                    {
                        data.insert(0, extra.clone());
                    }
                }
            }
        }
        data
    }
}

fn lookup_track_id(tap: &TapSnapshot) -> i64 {
    if tap.is_afk {
        tap.track_id
    } else {
        0
    }
}

fn overlay_track_id(tap: &TapSnapshot, catalog_id: Option<i64>) -> Option<i64> {
    catalog_id
        .filter(|id| *id > 0)
        .or_else(|| {
            let id = lookup_track_id(tap);
            (id > 0).then_some(id)
        })
}

fn should_unfave(already: bool, catalog_id: Option<i64>) -> bool {
    already && catalog_id.is_some_and(|id| id > 0)
}

const FAVE_EXTRA_ATTEMPTS: u32 = 2;

fn cache_nick(config: &FaveConfig) -> String {
    let list = config.list_nick.trim();
    if list.is_empty() {
        config.nick.trim().to_string()
    } else {
        list.to_string()
    }
}

fn open_irc(config: &FaveConfig) -> Result<TlsIrc, IrcError> {
    let (host, port, insecure) = match config.profile {
        IrcProfile::Rizon => (RIZON_HOST.to_string(), RIZON_PORT, false),
        IrcProfile::Bouncer => {
            let host = config.bouncer_host.trim();
            if host.is_empty() {
                return Err(IrcError::Protocol {
                    detail: "Bouncer host is required.".into(),
                });
            }
            let port = if config.bouncer_port == 0 {
                DEFAULT_BOUNCER_PORT
            } else {
                config.bouncer_port
            };
            (host.to_string(), port, config.allow_insecure_tls)
        }
    };
    connect_irc(
        &host,
        port,
        insecure,
        &config.client_cert_pem,
        &config.client_key_pem,
        &config.tls_fingerprint,
    )
}

impl RadioCore {
    fn fetch_news_entry(&self, id: i64) -> Result<(String, String, Vec<NewsComment>), ApiError> {
        let _fetch = self.news_fetch.lock().expect("news_fetch");
        if let Some((cached_id, html)) = self.http_cache.lock().expect("cache").news_html.as_ref() {
            if *cached_id == id {
                return Ok((
                    html.clone(),
                    parse_news_entry_body(html),
                    parse_news_comments(html),
                ));
            }
        }
        let html = self.client.get(&news_entry_url(id))?;
        self.remember_theme(&html);
        let body = parse_news_entry_body(&html);
        let comments = parse_news_comments(&html);
        self.http_cache.lock().expect("cache").news_html = Some((id, html.clone()));
        Ok((html, body, comments))
    }
}

#[uniffi::export]
impl RadioCore {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        let client = ReqwestApiClient::new()
            .map(|c| Arc::new(c) as Arc<dyn ApiClient>)
            .unwrap_or_else(|_| {
                struct Noop;
                impl ApiClient for Noop {
                    fn get(&self, _url: &str) -> Result<String, ApiError> {
                        Err(ApiError::Network {
                            detail: "http client unavailable".into(),
                        })
                    }
                    fn post_csrf(&self, _url: &str, _token: &str) -> Result<String, ApiError> {
                        Err(ApiError::Network {
                            detail: "http client unavailable".into(),
                        })
                    }
                }
                Arc::new(Noop)
            });
        Self::with_client(client)
    }

    pub fn start(self: Arc<Self>, listener: Box<dyn StatusListener>) {
        self.set_listener(Arc::from(listener));
        let mut slot = self.thread.lock().expect("thread");
        if slot.is_some() {
            return;
        }
        self.stop.store(false, Ordering::Relaxed);
        let this = Arc::clone(&self);
        *slot = Some(thread::spawn(move || {
            while !this.stop.load(Ordering::Relaxed) {
                let _ = this.poll_once();
                let delay = this.poll_delay();
                let mut slept = Duration::ZERO;
                while slept < delay && !this.stop.load(Ordering::Relaxed) {
                    if this.take_refetch() {
                        break;
                    }
                    thread::sleep(Duration::from_millis(100));
                    slept += Duration::from_millis(100);
                }
            }
        }));
    }

    pub fn stop(&self) {
        self.stop.store(true, Ordering::Relaxed);
        if let Some(handle) = self.thread.lock().expect("thread").take() {
            let _ = handle.join();
        }
    }

    pub fn set_ui_visible(&self, visible: bool) {
        self.ui_visible.store(visible, Ordering::Relaxed);
    }

    pub fn set_playing(&self, playing: bool) {
        self.playing.store(playing, Ordering::Relaxed);
    }

    pub fn is_playing(&self) -> bool {
        self.playing.load(Ordering::Relaxed)
    }

    pub fn on_icy_title(&self, title: String) {
        self.state
            .lock()
            .expect("state")
            .apply(NowPlayingEvent::IcyTitle(title), Self::unix_now());
    }

    pub fn on_stream_error(&self) {
        self.apply_stream_flag(NowPlayingEvent::StreamError)
    }

    pub fn on_stream_recovered(&self) {
        self.apply_stream_flag(NowPlayingEvent::StreamRecovered)
    }

    pub fn snapshot(&self) -> Option<Status> {
        self.state.lock().expect("state").status.clone()
    }

    pub fn refresh(&self) -> Result<(), ApiError> {
        self.poll_once()
    }

    pub fn progress(&self) -> Option<SongProgress> {
        let state = self.state.lock().expect("state");
        let status = state.status.as_ref()?;
        Some(song_progress(
            status,
            state.local_at_fetch,
            Self::unix_now(),
        ))
    }

    pub fn search(&self, query: String, page: i32) -> Result<SearchPage, ApiError> {
        let query = query.trim();
        if query.is_empty() {
            return Ok(SearchPage::empty());
        }
        let page = page.max(1);
        let _fetch = self.search_fetch.lock().expect("search_fetch");
        parse_search(&self.client.get(&search_url(query, page))?)
    }

    pub fn can_request(&self) -> Result<bool, ApiError> {
        parse_can_request(&self.client.get(CAN_REQUEST_URL)?)
    }

    pub fn news(&self, page: i32) -> Result<NewsPage, ApiError> {
        let page = page.max(1);
        let _fetch = self.news_fetch.lock().expect("news_fetch");
        let html = self.client.get(&news_list_url(page))?;
        self.remember_theme(&html);
        Ok(parse_news_list(&html, page))
    }

    pub fn staff(&self) -> Result<Vec<StaffMember>, ApiError> {
        let _fetch = self.staff_fetch.lock().expect("staff_fetch");
        let html = self.client.get(STAFF_URL)?;
        self.remember_theme(&html);
        Ok(parse_staff(&html))
    }

    pub fn schedule(&self) -> Result<Vec<ScheduleDay>, ApiError> {
        let _fetch = self.schedule_fetch.lock().expect("schedule_fetch");
        let html = self.client.get(SCHEDULE_URL)?;
        self.remember_theme(&html);
        Ok(parse_schedule(&html))
    }

    pub fn theme_name(&self) -> Option<String> {
        self.theme_name.lock().expect("theme_name").clone()
    }

    pub fn sniff_theme(&self) -> Result<Option<String>, ApiError> {
        let html = self.client.get(SITE_URL)?;
        self.remember_theme(&html);
        Ok(self.theme_name())
    }

    fn remember_theme(&self, html: &str) {
        if let Some(name) = parse_theme_name(html) {
            *self.theme_name.lock().expect("theme_name") = Some(name);
        }
    }

    pub fn news_article(&self, id: i64) -> Result<NewsArticle, ApiError> {
        if id <= 0 {
            return Err(ApiError::Decode {
                detail: "missing news id".into(),
            });
        }
        let (_, body, _) = self.fetch_news_entry(id)?;
        Ok(NewsArticle {
            id,
            title: String::new(),
            header: String::new(),
            text: body,
            updated_at: String::new(),
            author: NewsAuthor {
                id: 0,
                user: String::new(),
                role: String::new(),
            },
        })
    }

    pub fn news_comments(&self, id: i64) -> Result<Vec<NewsComment>, ApiError> {
        if id <= 0 {
            return Ok(Vec::new());
        }
        let (_, _, comments) = self.fetch_news_entry(id)?;
        Ok(comments)
    }

    pub fn post_news_comment(&self, id: i64, body: String) -> Result<Vec<NewsComment>, ApiError> {
        let body = body.trim().to_string();
        if id <= 0 {
            return Err(ApiError::Decode {
                detail: "missing news id".into(),
            });
        }
        if body.is_empty() {
            return Err(ApiError::Decode {
                detail: "Comment is empty.".into(),
            });
        }
        if body.chars().count() > COMMENT_MAX as usize {
            return Err(ApiError::Decode {
                detail: "Comment is too long.".into(),
            });
        }
        let url = news_entry_url(id);
        let (html, _, _) = self.fetch_news_entry(id)?;
        let token = extract_csrf_token(&html)?;
        let posted = match self
            .client
            .post_form(&url, &token, vec![("comment".into(), body.clone())])
        {
            Ok(html) => html,
            Err(ApiError::Http { status: 403, .. }) => {
                let fetched = self.client.get(&url)?;
                let token = extract_csrf_token(&fetched)?;
                self.client
                    .post_form(&url, &token, vec![("comment".into(), body)])?
            }
            Err(e) => return Err(e),
        };
        let comments = parse_news_comments(&posted);
        self.http_cache.lock().expect("cache").news_html = Some((id, posted));
        Ok(comments)
    }

    pub fn favorites(&self, nick: String, page: i32) -> Result<FavoritesPage, ApiError> {
        let nick = nick.trim();
        if nick.is_empty() {
            return Ok(FavoritesPage::empty());
        }
        let page = page.max(1);
        let data = self.faves_rows(nick, page)?;
        let last_page = self.resolve_faves_last(nick, page, &data)?;
        let (last_page, data) = self.trim_faves_last(nick, page, last_page, data)?;
        Ok(FavoritesPage {
            current_page: page,
            last_page,
            data: self.apply_fave_overlay(nick, page, data),
        })
    }

    pub fn is_favorite(&self, nick: String, track_id: i64, np: String) -> bool {
        let nick = nick.trim();
        if nick.is_empty() {
            return false;
        }
        let cache = self.http_cache.lock().expect("cache");
        if cache
            .fave_minus
            .get(nick)
            .into_iter()
            .flatten()
            .any(|row| row_is_song(row, track_id, &np))
        {
            return false;
        }
        if cache
            .fave_plus
            .get(nick)
            .into_iter()
            .flatten()
            .any(|row| row_is_song(row, track_id, &np))
        {
            return true;
        }
        cache
            .membership
            .get(nick)
            .into_iter()
            .flatten()
            .any(|row| row_is_song(row, track_id, &np))
    }

    pub fn import_membership(&self, nick: String, rows: Vec<FavoriteRow>) {
        let nick = nick.trim();
        if nick.is_empty() {
            return;
        }
        self.http_cache
            .lock()
            .expect("cache")
            .membership
            .insert(nick.to_string(), rows);
    }

    pub fn export_membership(&self, nick: String) -> Vec<FavoriteRow> {
        let nick = nick.trim();
        if nick.is_empty() {
            return Vec::new();
        }
        let cache = self.http_cache.lock().expect("cache");
        let mut data = cache
            .membership
            .get(nick)
            .cloned()
            .unwrap_or_default();
        if let Some(minus) = cache.fave_minus.get(nick) {
            data.retain(|row| {
                !minus
                    .iter()
                    .any(|m| row_is_song(m, row.tracks_id.unwrap_or(0), &row.meta))
            });
        }
        if let Some(plus) = cache.fave_plus.get(nick) {
            for extra in plus.iter().rev() {
                if !data
                    .iter()
                    .any(|row| row_is_song(extra, row.tracks_id.unwrap_or(0), &row.meta))
                {
                    data.insert(0, extra.clone());
                }
            }
        }
        data
    }

    pub fn keep_membership(&self, nick: String) {
        self.keep_memberships(vec![nick]);
    }

    pub fn keep_memberships(&self, nicks: Vec<String>) {
        let keep: HashSet<String> = nicks
            .into_iter()
            .map(|n| n.trim().to_string())
            .filter(|n| !n.is_empty())
            .collect();
        let mut cache = self.http_cache.lock().expect("cache");
        if keep.is_empty() {
            cache.membership.clear();
            cache.fave_plus.clear();
            cache.fave_minus.clear();
            return;
        }
        cache.membership.retain(|k, _| keep.contains(k));
        cache.fave_plus.retain(|k, _| keep.contains(k));
        cache.fave_minus.retain(|k, _| keep.contains(k));
    }

    fn fetch_all_fave_rows(&self, nick: &str) -> Result<Vec<FavoriteRow>, ApiError> {
        let first = self.faves_rows(nick, 1)?;
        let last = self.resolve_faves_last(nick, 1, &first)?;
        let mut rows = first;
        for page in 2..=last.max(1) {
            rows.extend(self.faves_rows(nick, page)?);
        }
        Ok(rows)
    }

    pub fn prefetch_favorites(&self, nick: String) -> Result<(), ApiError> {
        let nick = nick.trim();
        if nick.is_empty() {
            return Ok(());
        }
        let rows = self.fetch_all_fave_rows(nick)?;
        let mut cache = self.http_cache.lock().expect("cache");
        cache.membership.insert(nick.to_string(), rows.clone());
        if let Some(plus) = cache.fave_plus.get_mut(nick) {
            plus.retain(|row| {
                !rows
                    .iter()
                    .any(|have| row_is_song(have, row.tracks_id.unwrap_or(0), &row.meta))
            });
        }
        if cache
            .fave_plus
            .get(nick)
            .is_some_and(|rows| rows.is_empty())
        {
            cache.fave_plus.remove(nick);
        }
        if let Some(minus) = cache.fave_minus.get_mut(nick) {
            minus.retain(|row| {
                rows.iter()
                    .any(|have| row_is_song(have, row.tracks_id.unwrap_or(0), &row.meta))
            });
        }
        if cache
            .fave_minus
            .get(nick)
            .is_some_and(|rows| rows.is_empty())
        {
            cache.fave_minus.remove(nick);
        }
        Ok(())
    }

    pub fn add_fave(&self, config: FaveConfig) -> FaveResult {
        let nick = config.nick.trim().to_string();
        if nick_is_empty(&nick) {
            return FaveResult::noop();
        }
        let Ok(_guard) = self.fave_lock.try_lock() else {
            return FaveResult::failed("A fave is already in progress.");
        };
        let Some(status) = self.snapshot() else {
            return FaveResult::failed("No station status yet.");
        };
        let tap = TapSnapshot {
            is_afk: status.is_afk_stream,
            track_id: status.track_id,
            np: status.np.clone(),
        };
        let list_nick = cache_nick(&config);
        let lookup_id = lookup_track_id(&tap);
        let catalog_id = self.catalog_id_for(&list_nick, &tap);
        let unfave = should_unfave(
            self.is_favorite(list_nick.clone(), lookup_id, tap.np.clone()),
            catalog_id,
        );
        let attach = attach_nick(config.profile, &nick);
        let mut last_err = None;
        for attempt in 0..=FAVE_EXTRA_ATTEMPTS {
            let mut conn = match open_irc(&config) {
                Ok(c) => c,
                Err(e) if e.is_retryable() && attempt < FAVE_EXTRA_ATTEMPTS => {
                    last_err = Some(e);
                    continue;
                }
                Err(e) => return FaveResult::failed(e.user_message()),
            };
            let latest = || self.snapshot().map(|s| s.np).unwrap_or_default();
            let result = match run_add_fave(
                &mut conn,
                &config,
                &tap,
                latest,
                &attach,
                unfave,
                catalog_id,
            ) {
                Ok(r) => r,
                Err(e) if e.is_retryable() && attempt < FAVE_EXTRA_ATTEMPTS => {
                    let _ = conn.close_notify();
                    last_err = Some(e);
                    continue;
                }
                Err(e) => {
                    let _ = conn.close_notify();
                    return FaveResult::failed(e.user_message());
                }
            };
            let _ = conn.close_notify();
            if result.kind == FaveKind::Success {
                self.remember_toggle(&list_nick, &tap, catalog_id, result.favorited);
                if list_nick != nick {
                    self.remember_toggle(&nick, &tap, catalog_id, result.favorited);
                }
            }
            return result;
        }
        FaveResult::failed(
            last_err
                .map(|e| e.user_message())
                .unwrap_or_else(|| "Fave failed.".into()),
        )
    }

    pub fn probe_irc(&self, config: FaveConfig) -> FaveResult {
        let nick = config.nick.trim().to_string();
        if nick_is_empty(&nick) {
            return FaveResult::failed("Set a connection nick.");
        }
        let Ok(_guard) = self.fave_lock.try_lock() else {
            return FaveResult::failed("A fave is already in progress.");
        };
        let attach = attach_nick(config.profile, &nick);
        let mut conn = match open_irc(&config) {
            Ok(c) => c,
            Err(e) => return FaveResult::failed(e.user_message()),
        };
        let result = match run_probe(&mut conn, &config, &attach) {
            Ok(()) => FaveResult {
                kind: FaveKind::Success,
                message: connected_message(conn.server_fingerprint()),
                favorited: false,
            },
            Err(e) => FaveResult::failed(e.user_message()),
        };
        let _ = conn.close_notify();
        result
    }

    pub fn request(&self, track_id: i64) -> Result<RequestResult, ApiError> {
        let once = || {
            let html = self.client.get(CSRF_BOOTSTRAP_URL)?;
            let token = extract_csrf_token(&html)?;
            self.client.post_csrf(&request_url(track_id), &token)
        };
        match once() {
            Ok(body) => parse_request_result(&body),
            Err(ApiError::Http { status: 403, .. }) => parse_request_result(&once()?),
            Err(e) => Err(e),
        }
    }

    pub fn request_random_favorite(&self, nick: String) -> Result<RequestResult, ApiError> {
        let nick = nick.trim();
        if nick.is_empty() {
            return Ok(RequestResult {
                ok: false,
                message: "Set your Rizon nick in Favorites.".into(),
            });
        }
        let first = self.favorites(nick.to_string(), 1)?;
        let mut rows = first.data;
        for page in 2..=first.last_page.max(1) {
            rows.extend(self.favorites(nick.to_string(), page)?.data);
        }
        let now = self
            .snapshot()
            .map(|s| s.current)
            .unwrap_or_else(Self::unix_now);
        let slot = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_nanos() as u64)
            .unwrap_or(0);
        let Some(id) = pick_requestable_id(&rows, now, slot) else {
            return Ok(RequestResult {
                ok: false,
                message: "No requestable favorites.".into(),
            });
        };
        self.request(id)
    }
}

impl Drop for RadioCore {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::irc::TapSnapshot;

    struct FixtureClient(&'static str);
    impl ApiClient for FixtureClient {
        fn get(&self, _url: &str) -> Result<String, ApiError> {
            Ok(self.0.into())
        }
        fn post_csrf(&self, _url: &str, _token: &str) -> Result<String, ApiError> {
            Err(ApiError::Network {
                detail: "no post".into(),
            })
        }
    }

    fn core() -> Arc<RadioCore> {
        RadioCore::with_client(Arc::new(FixtureClient(include_str!(
            "../tests/fixtures/faves.json"
        ))))
    }

    fn np() -> &'static str {
        "Mori Yuuya - Seitokai Yakuindomo no March"
    }

    #[test]
    fn is_playing_follows_set_playing() {
        let core = core();
        assert!(!core.is_playing());
        core.set_playing(true);
        assert!(core.is_playing());
        core.set_playing(false);
        assert!(!core.is_playing());
    }

    fn membership_row(id: i64, meta: &str) -> FavoriteRow {
        FavoriteRow {
            tracks_id: Some(id),
            meta: meta.into(),
            artist: String::new(),
            title: meta.into(),
            last_requested: None,
            last_played: None,
            request_count: None,
        }
    }

    #[test]
    fn keep_membership_drops_other_nicks_and_empty_clears() {
        let core = core();
        core.import_membership(
            "Alice".into(),
            vec![membership_row(1, "A - One")],
        );
        core.import_membership(
            "Bob".into(),
            vec![membership_row(2, "B - Two")],
        );
        assert!(core.is_favorite("Alice".into(), 1, "A - One".into()));
        assert!(core.is_favorite("Bob".into(), 2, "B - Two".into()));
        core.keep_membership("Bob".into());
        assert!(!core.is_favorite("Alice".into(), 1, "A - One".into()));
        assert!(core.is_favorite("Bob".into(), 2, "B - Two".into()));
        core.keep_membership("  ".into());
        assert!(!core.is_favorite("Bob".into(), 2, "B - Two".into()));
    }

    #[test]
    fn keep_memberships_keeps_both_typed_nicks() {
        let core = core();
        core.import_membership(
            "Alice".into(),
            vec![membership_row(1, "A - One")],
        );
        core.import_membership(
            "Bob".into(),
            vec![membership_row(2, "B - Two")],
        );
        core.import_membership(
            "Peek".into(),
            vec![membership_row(3, "C - Three")],
        );
        core.keep_memberships(vec!["Alice".into(), "Bob".into()]);
        assert!(core.is_favorite("Alice".into(), 1, "A - One".into()));
        assert!(core.is_favorite("Bob".into(), 2, "B - Two".into()));
        assert!(!core.is_favorite("Peek".into(), 3, "C - Three".into()));
    }

    #[test]
    fn unfave_only_when_catalog_id_known() {
        assert!(!should_unfave(true, None));
        assert!(!should_unfave(true, Some(0)));
        assert!(!should_unfave(false, Some(42)));
        assert!(should_unfave(true, Some(42)));
    }

    #[test]
    fn remember_toggle_applies_to_list_and_irc_nick() {
        let core = core();
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: np().into(),
        };
        core.remember_toggle("List", &tap, Some(42), true);
        core.remember_toggle("Irc", &tap, Some(42), true);
        assert!(core.is_favorite("List".into(), 42, np().into()));
        assert!(core.is_favorite("Irc".into(), 42, np().into()));
    }

    #[test]
    fn overlay_toggle_fills_then_unfills_without_wiping_membership() {
        let core = core();
        core.prefetch_favorites("Geiravor".into()).unwrap();
        assert!(core.is_favorite("Geiravor".into(), 6130, np().into()));
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 6130,
            np: np().into(),
        };
        assert_eq!(core.catalog_id_for("Geiravor", &tap), Some(6130));
        core.remember_toggle("Geiravor", &tap, Some(6130), false);
        assert!(!core.is_favorite("Geiravor".into(), 6130, np().into()));
        assert!(!core
            .favorites("Geiravor".into(), 1)
            .unwrap()
            .data
            .iter()
            .any(|r| r.tracks_id == Some(6130)));
        core.remember_toggle("Geiravor", &tap, Some(6130), true);
        assert!(core.is_favorite("Geiravor".into(), 6130, np().into()));
        assert!(core
            .favorites("Geiravor".into(), 1)
            .unwrap()
            .data
            .iter()
            .any(|r| r.tracks_id == Some(6130)));
    }

    #[test]
    fn live_dj_leftover_trackid_is_not_a_catalog_id() {
        let core = core();
        core.prefetch_favorites("Geiravor".into()).unwrap();
        let tap = TapSnapshot {
            is_afk: false,
            track_id: 99,
            np: "DJ - Only".into(),
        };
        assert_eq!(core.catalog_id_for("Geiravor", &tap), None);
        core.remember_toggle("Geiravor", &tap, None, true);
        assert!(core.is_favorite("Geiravor".into(), 0, "DJ - Only".into()));
        assert_eq!(core.catalog_id_for("Geiravor", &tap), None);
        assert!(!should_unfave(
            core.is_favorite("Geiravor".into(), 0, "DJ - Only".into()),
            core.catalog_id_for("Geiravor", &tap)
        ));
    }

    #[test]
    fn prefetch_keeps_overlay_until_get_includes_the_song() {
        let core = core();
        core.prefetch_favorites("Geiravor".into()).unwrap();
        let added = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: "New - Song".into(),
        };
        core.remember_toggle("Geiravor", &added, Some(42), true);
        core.prefetch_favorites("Geiravor".into()).unwrap();
        assert!(core.is_favorite("Geiravor".into(), 42, "New - Song".into()));
        let gone = TapSnapshot {
            is_afk: true,
            track_id: 6130,
            np: np().into(),
        };
        core.remember_toggle("Geiravor", &gone, Some(6130), false);
        core.prefetch_favorites("Geiravor".into()).unwrap();
        assert!(!core.is_favorite("Geiravor".into(), 6130, np().into()));
    }

    #[test]
    fn afk_snapshot_id_is_catalog_id_even_when_not_listed() {
        let core = core();
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: "Hirasawa Susumu - Gats".into(),
        };
        assert_eq!(core.catalog_id_for("Geiravor", &tap), Some(42));
        assert!(!should_unfave(
            core.is_favorite("Geiravor".into(), 42, tap.np.clone()),
            core.catalog_id_for("Geiravor", &tap)
        ));
        core.remember_toggle("Geiravor", &tap, Some(42), true);
        assert!(should_unfave(
            core.is_favorite("Geiravor".into(), 42, tap.np.clone()),
            core.catalog_id_for("Geiravor", &tap)
        ));
    }

    #[test]
    fn live_dj_matching_fave_row_is_catalog_id() {
        let core = core();
        core.prefetch_favorites("Geiravor".into()).unwrap();
        let tap = TapSnapshot {
            is_afk: false,
            track_id: 99,
            np: np().into(),
        };
        assert_eq!(core.catalog_id_for("Geiravor", &tap), Some(6130));
        assert!(should_unfave(
            core.is_favorite("Geiravor".into(), 0, np().into()),
            core.catalog_id_for("Geiravor", &tap)
        ));
    }
}
