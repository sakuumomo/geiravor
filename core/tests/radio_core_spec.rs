use std::sync::{Arc, Mutex};

use geiravor_core::{ApiClient, ApiError, RadioCore, Status, StatusListener, poll_interval};

struct Switchable {
    next: Mutex<Result<String, ApiError>>,
}
impl ApiClient for Switchable {
    fn get(&self, _url: &str) -> Result<String, ApiError> {
        self.next.lock().expect("lock").clone()
    }

    fn post_csrf(&self, _url: &str, _token: &str) -> Result<String, ApiError> {
        Err(ApiError::Network {
            detail: "no post".into(),
        })
    }
}

struct UrlClient {
    last: Mutex<String>,
    body: String,
}
impl ApiClient for UrlClient {
    fn get(&self, url: &str) -> Result<String, ApiError> {
        *self.last.lock().expect("lock") = url.to_string();
        Ok(self.body.clone())
    }

    fn post_csrf(&self, url: &str, _token: &str) -> Result<String, ApiError> {
        *self.last.lock().expect("lock") = url.to_string();
        Ok(include_str!("fixtures/request_success.json").into())
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
    let core = RadioCore::with_client(Arc::new(Switchable {
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
    let core = RadioCore::with_client(fetcher.clone());
    core.tick(10).unwrap();
    *fetcher.next.lock().expect("lock") = Err(ApiError::Network {
        detail: "network".into(),
    });
    assert!(matches!(
        core.tick(11),
        Err(ApiError::Network { detail }) if detail == "network"
    ));
    assert_eq!(core.snapshot().expect("kept").title, "Gats");
    assert_eq!(
        core.poll_delay().as_secs(),
        poll_interval(true, false, 1).as_secs()
    );
}

#[test]
fn decode_failure_counts_toward_backoff() {
    let fetcher = Arc::new(Switchable {
        next: Mutex::new(Ok("not json".to_string())),
    });
    let core = RadioCore::with_client(fetcher);
    assert!(matches!(core.tick(10), Err(ApiError::Decode { .. })));
    assert_eq!(
        core.poll_delay().as_secs(),
        poll_interval(false, false, 1).as_secs()
    );
}

#[test]
fn icy_does_not_replace_np() {
    let json = include_str!("fixtures/api_snapshot.json");
    let core = RadioCore::with_client(Arc::new(Switchable {
        next: Mutex::new(Ok(json.to_string())),
    }));
    core.tick(10).unwrap();
    let np = core.snapshot().unwrap().np;
    core.on_icy_title("Other - Song".into());
    assert_eq!(core.snapshot().unwrap().np, np);
    assert!(core.take_refetch());
}

#[test]
fn search_uses_json_api_path_and_empty_query_skips_http() {
    let client = Arc::new(UrlClient {
        last: Mutex::new(String::new()),
        body: include_str!("fixtures/search_page.json").into(),
    });
    let core = RadioCore::with_client(client.clone());
    let empty = core.search("  ".into(), 1).unwrap();
    assert!(empty.data.is_empty());
    assert!(client.last.lock().expect("lock").is_empty());
    let page = core.search("Aimer with chelly (EGOIST)".into(), 1).unwrap();
    assert_eq!(page.data[0].id, 10136);
    assert!(!page.data[1].requestable);
    assert!(
        client
            .last
            .lock()
            .expect("lock")
            .starts_with("https://r-a-d.io/api/search/")
    );
}

#[test]
fn can_request_uses_capital_main_endpoint() {
    let client = Arc::new(UrlClient {
        last: Mutex::new(String::new()),
        body: include_str!("fixtures/can_request.json").into(),
    });
    let core = RadioCore::with_client(client.clone());
    assert!(core.can_request().unwrap());
    assert_eq!(
        *client.last.lock().expect("lock"),
        "https://r-a-d.io/api/can-request"
    );
}

#[test]
fn request_posts_track_id_after_csrf_bootstrap() {
    let client = Arc::new(UrlClient {
        last: Mutex::new(String::new()),
        body: include_str!("fixtures/csrf_token_comment.html").into(),
    });
    let core = RadioCore::with_client(client.clone());
    let result = core.request(10136).unwrap();
    assert!(result.ok);
    assert_eq!(
        *client.last.lock().expect("lock"),
        "https://r-a-d.io/request/10136"
    );
}
