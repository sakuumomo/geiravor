//! Process-wide domain. UniFFI constructor takes `files_dir` from the shell.

use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use crate::error::ApiError;
use crate::irc::{
    FaveConfig, FaveResult, IrcProfile, RIZON_HOST, RIZON_PORT, TapSnapshot, attach_nick,
    connect_irc, irc_nick, nick_is_empty, run_add_fave, with_retries,
};
use crate::net::{Coalescer, HttpClient, ReqwestClient};
use crate::parse::{API_URL, Status, parse_status, parse_status_str};
use crate::poll::poll_interval;
use crate::reducer::{NowPlayingEvent, NowPlayingState, reduce};
use crate::store::Store;

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
        let _ = std::fs::remove_dir_all(&dir);
    }
}
