use std::sync::{Arc, Mutex};

use geiravor_core::{poll_interval, Fetcher, RadioCore, Status, StatusListener};

struct Switchable {
    next: Mutex<Result<String, String>>,
}
impl Fetcher for Switchable {
    fn get(&self, _url: &str) -> Result<String, String> {
        self.next.lock().expect("lock").clone()
    }
}

struct RecordingListener {
    last: Mutex<Option<(Status, bool)>>,
}
impl StatusListener for RecordingListener {
    fn on_update(&self, status: Status, stream_down: bool) {
        *self.last.lock().expect("lock") = Some((status, stream_down));
    }
}

#[test]
fn tick_notifies_parsed_now_playing() {
    let json = include_str!("fixtures/api_snapshot.json");
    let listener = Arc::new(RecordingListener {
        last: Mutex::new(None),
    });
    let core = RadioCore::with_fetcher(Arc::new(Switchable {
        next: Mutex::new(Ok(json.to_string())),
    }));
    core.set_listener(listener.clone());
    core.tick(1_787_672_409).expect("tick");
    let snap = core.snapshot().expect("snapshot");
    assert_eq!(snap.artist, "Hirasawa Susumu");
    assert_eq!(snap.title, "Gats");
    let (got, down) = listener.last.lock().expect("lock").clone().expect("notify");
    assert_eq!(got.np, snap.np);
    assert!(!down);
}

#[test]
fn failed_tick_keeps_previous_snapshot_and_backs_off() {
    let json = include_str!("fixtures/api_snapshot.json");
    let fetcher = Arc::new(Switchable {
        next: Mutex::new(Ok(json.to_string())),
    });
    let core = RadioCore::with_fetcher(fetcher.clone());
    core.tick(10).unwrap();
    *fetcher.next.lock().expect("lock") = Err("network".into());
    assert!(core.tick(11).is_err());
    assert_eq!(core.snapshot().expect("kept").title, "Gats");
    assert_eq!(
        core.poll_delay().as_secs(),
        poll_interval(true, false, 1).as_secs()
    );
}

#[test]
fn icy_does_not_replace_np() {
    let json = include_str!("fixtures/api_snapshot.json");
    let core = RadioCore::with_fetcher(Arc::new(Switchable {
        next: Mutex::new(Ok(json.to_string())),
    }));
    core.tick(10).unwrap();
    let np = core.snapshot().unwrap().np;
    core.on_icy_title("Other - Song".into());
    assert_eq!(core.snapshot().unwrap().np, np);
    assert!(core.take_refetch());
}
