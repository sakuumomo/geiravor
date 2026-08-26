use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::poll::poll_interval;
use crate::progress::{song_progress, SongProgress};
use crate::reducer::{NowPlayingEvent, NowPlayingState};
use crate::status::{parse_status, Status, API_URL};
use crate::USER_AGENT;

/// HTTP for `/api` now; 0.2.0 search/request/faves add methods here (`006-requests-faves.md`).
pub trait ApiClient: Send + Sync {
    fn get(&self, url: &str) -> Result<String, String>;
}

struct ReqwestApiClient {
    client: reqwest::blocking::Client,
}

impl ReqwestApiClient {
    fn new() -> Result<Self, String> {
        let client = reqwest::blocking::Client::builder()
            .user_agent(USER_AGENT)
            .build()
            .map_err(|e| e.to_string())?;
        Ok(Self { client })
    }
}

impl ApiClient for ReqwestApiClient {
    fn get(&self, url: &str) -> Result<String, String> {
        self.client
            .get(url)
            .send()
            .and_then(|r| r.error_for_status())
            .and_then(|r| r.text())
            .map_err(|e| e.to_string())
    }
}

#[uniffi::export(callback_interface)]
pub trait StatusListener: Send + Sync {
    fn on_update(&self, status: Status, stream_down: bool);
}

#[derive(uniffi::Object)]
pub struct RadioCore {
    client: Arc<dyn ApiClient>,
    state: Mutex<NowPlayingState>,
    listener: Mutex<Option<Arc<dyn StatusListener>>>,
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

    pub fn tick(&self, now: i64) -> Result<(), String> {
        let body = match self.client.get(API_URL) {
            Ok(body) => body,
            Err(e) => {
                self.failures.fetch_add(1, Ordering::Relaxed);
                return Err(e);
            }
        };
        let status = parse_status(&body).map_err(|e| e.0)?;
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

    fn unix_now() -> i64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0)
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
                    fn get(&self, _url: &str) -> Result<String, String> {
                        Err("http client unavailable".into())
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
                let _ = this.tick(RadioCore::unix_now());
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
        let mut state = self.state.lock().expect("state");
        state.apply(NowPlayingEvent::StreamError, Self::unix_now());
        let down = state.stream_down;
        let status = state.status.clone();
        drop(state);
        if let Some(status) = status {
            self.notify(status, down);
        }
    }

    pub fn snapshot(&self) -> Option<Status> {
        self.state.lock().expect("state").status.clone()
    }

    pub fn progress(&self) -> Option<SongProgress> {
        let state = self.state.lock().expect("state");
        let status = state.status.as_ref()?;
        Some(song_progress(status, state.local_at_fetch, Self::unix_now()))
    }
}

impl Drop for RadioCore {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
    }
}
