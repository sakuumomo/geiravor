use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::csrf::{CSRF_BOOTSTRAP_URL, extract_csrf_token, post_with_csrf};
use crate::favorites::{
    FAVES_PER_PAGE, FavoriteRow, FavoritesPage, discover_last_page, faves_html_url, faves_url,
    parse_faves, parse_faves_last_page, PageSample,
};
use crate::http::{ApiError, blocking_client};
use crate::poll::poll_interval;
use crate::progress::{SongProgress, song_progress};
use crate::reducer::{NowPlayingEvent, NowPlayingState};
use crate::search::{
    CAN_REQUEST_URL, RequestResult, SearchPage, parse_can_request, parse_request_result,
    parse_search, request_url, search_url,
};
use crate::status::{API_URL, Status, parse_status};

/// HTTP for `/api` now; 0.2.0 search/request/faves add methods here (`006-requests-faves.md`).
pub trait ApiClient: Send + Sync {
    fn get(&self, url: &str) -> Result<String, ApiError>;
    fn post_csrf(&self, url: &str, token: &str) -> Result<String, ApiError>;
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
}

#[uniffi::export(callback_interface)]
pub trait StatusListener: Send + Sync {
    fn on_update(&self, status: Status, stream_down: bool);
}

#[derive(Default)]
struct HttpCache {
    search: HashMap<(String, i32), SearchPage>,
    faves: HashMap<(String, i32), Vec<FavoriteRow>>,
    faves_last: HashMap<String, i32>,
}

#[derive(uniffi::Object)]
pub struct RadioCore {
    client: Arc<dyn ApiClient>,
    state: Mutex<NowPlayingState>,
    listener: Mutex<Option<Arc<dyn StatusListener>>>,
    http_cache: Mutex<HttpCache>,
    search_fetch: Mutex<()>,
    faves_fetch: Mutex<()>,
    ui_visible: AtomicBool,
    playing: AtomicBool,
    failures: AtomicU32,
    stop: Arc<AtomicBool>,
    thread: Mutex<Option<JoinHandle<()>>>,
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
            ui_visible: AtomicBool::new(false),
            playing: AtomicBool::new(false),
            failures: AtomicU32::new(0),
            stop: Arc::new(AtomicBool::new(false)),
            thread: Mutex::new(None),
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

    fn cached_search(&self, query: &str, page: i32) -> Option<SearchPage> {
        self.http_cache
            .lock()
            .expect("cache")
            .search
            .get(&(query.to_string(), page))
            .cloned()
    }

    fn cached_faves(&self, nick: &str, page: i32) -> Option<Vec<FavoriteRow>> {
        self.http_cache
            .lock()
            .expect("cache")
            .faves
            .get(&(nick.to_string(), page))
            .cloned()
    }

    fn cached_faves_last(&self, nick: &str) -> Option<i32> {
        self.http_cache
            .lock()
            .expect("cache")
            .faves_last
            .get(nick)
            .copied()
    }

    fn faves_rows(&self, nick: &str, page: i32) -> Result<Vec<FavoriteRow>, ApiError> {
        if let Some(hit) = self.cached_faves(nick, page) {
            return Ok(hit);
        }
        let _fetch = self.faves_fetch.lock().expect("faves_fetch");
        if let Some(hit) = self.cached_faves(nick, page) {
            return Ok(hit);
        }
        let rows = parse_faves(&self.client.get(&faves_url(nick, page))?)?;
        self.http_cache
            .lock()
            .expect("cache")
            .faves
            .insert((nick.to_string(), page), rows.clone());
        Ok(rows)
    }

    fn resolve_faves_last(
        &self,
        nick: &str,
        page: i32,
        data: &[FavoriteRow],
    ) -> Result<i32, ApiError> {
        if let Some(last) = self.cached_faves_last(nick) {
            return Ok(last);
        }
        let last = if (data.len() as i32) < FAVES_PER_PAGE {
            page
        } else {
            let html_last = self
                .client
                .get(&faves_html_url(nick))
                .map(|html| parse_faves_last_page(&html))
                .unwrap_or(1);
            if html_last > 1 {
                html_last
            } else {
                discover_last_page(page.max(1), |probe| {
                    self.faves_rows(nick, probe)
                        .map(|rows| PageSample::from_rows(&rows))
                        .unwrap_or_else(|_| PageSample::from_parts(0, ""))
                })
            }
        };
        let mut cache = self.http_cache.lock().expect("cache");
        Ok(*cache.faves_last.entry(nick.to_string()).or_insert(last))
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
        if let Some(hit) = self.cached_search(query, page) {
            return Ok(hit);
        }
        let _fetch = self.search_fetch.lock().expect("search_fetch");
        if let Some(hit) = self.cached_search(query, page) {
            return Ok(hit);
        }
        let parsed = parse_search(&self.client.get(&search_url(query, page))?)?;
        self.http_cache
            .lock()
            .expect("cache")
            .search
            .insert((query.to_string(), page), parsed.clone());
        Ok(parsed)
    }

    pub fn can_request(&self) -> Result<bool, ApiError> {
        parse_can_request(&self.client.get(CAN_REQUEST_URL)?)
    }

    pub fn favorites(&self, nick: String, page: i32) -> Result<FavoritesPage, ApiError> {
        let nick = nick.trim();
        if nick.is_empty() {
            return Ok(FavoritesPage::empty());
        }
        let page = page.max(1);
        let data = self.faves_rows(nick, page)?;
        let last_page = self.resolve_faves_last(nick, page, &data)?;
        Ok(FavoritesPage {
            current_page: page,
            last_page,
            data,
        })
    }

    pub fn prefetch_favorites(&self, nick: String) -> Result<(), ApiError> {
        if nick.trim().is_empty() {
            return Ok(());
        }
        let _ = self.favorites(nick, 1)?;
        Ok(())
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
}

impl Drop for RadioCore {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
    }
}
