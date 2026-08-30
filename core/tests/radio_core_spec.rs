use std::sync::{Arc, Mutex};

use geiravor_core::{
    ApiClient, ApiError, FaveConfig, FaveKind, IrcProfile, RadioCore, Status, StatusListener,
    poll_interval,
};

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
    urls: Mutex<Vec<String>>,
}
impl UrlClient {
    fn new(body: impl Into<String>) -> Self {
        Self {
            last: Mutex::new(String::new()),
            body: body.into(),
            urls: Mutex::new(Vec::new()),
        }
    }
}
impl ApiClient for UrlClient {
    fn get(&self, url: &str) -> Result<String, ApiError> {
        *self.last.lock().expect("lock") = url.to_string();
        self.urls.lock().expect("urls").push(url.to_string());
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
    let client = Arc::new(UrlClient::new(include_str!("fixtures/search_page.json")));
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
    *client.last.lock().expect("lock") = String::new();
    let again = core.search("Aimer with chelly (EGOIST)".into(), 1).unwrap();
    assert_eq!(again.data[0].id, 10136);
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
    let client = Arc::new(UrlClient::new(include_str!("fixtures/can_request.json")));
    let core = RadioCore::with_client(client.clone());
    assert!(core.can_request().unwrap());
    assert_eq!(
        *client.last.lock().expect("lock"),
        "https://r-a-d.io/api/can-request"
    );
}

#[test]
fn news_hits_html_list_every_time() {
    let client = Arc::new(UrlClient::new(include_str!("fixtures/news_list.html")));
    let core = RadioCore::with_client(client.clone());
    let page = core.news(1).unwrap();
    assert_eq!(page.data.len(), 2);
    assert_eq!(page.data[0].id, 82);
    assert_eq!(page.data[0].author.user, "claud");
    assert_eq!(page.last_page, 4);
    assert_eq!(
        *client.last.lock().expect("lock"),
        "https://r-a-d.io/news"
    );
    *client.last.lock().expect("lock") = String::new();
    let again = core.news(1).unwrap();
    assert_eq!(again.data[1].title, "Holid/a/y Stre/a/ms 2025 Schedule");
    assert_eq!(
        *client.last.lock().expect("lock"),
        "https://r-a-d.io/news"
    );
}

struct NewsRouteClient {
    json: String,
    list: String,
    entry: String,
    urls: Mutex<Vec<String>>,
    posts: Mutex<Vec<(String, Vec<(String, String)>)>>,
}
impl ApiClient for NewsRouteClient {
    fn get(&self, url: &str) -> Result<String, ApiError> {
        self.urls.lock().expect("urls").push(url.to_string());
        if url.contains("/news/") {
            Ok(self.entry.clone())
        } else if url.contains("/news") {
            Ok(self.list.clone())
        } else {
            Ok(self.json.clone())
        }
    }

    fn post_csrf(&self, url: &str, _token: &str) -> Result<String, ApiError> {
        self.urls.lock().expect("urls").push(url.to_string());
        Ok(self.entry.clone())
    }

    fn post_form(
        &self,
        url: &str,
        _token: &str,
        fields: Vec<(String, String)>,
    ) -> Result<String, ApiError> {
        self.urls.lock().expect("urls").push(url.to_string());
        self.posts.lock().expect("posts").push((url.to_string(), fields));
        Ok(self.entry.clone())
    }
}

#[test]
fn news_attaches_html_ids_and_posts_comment() {
    let client = Arc::new(NewsRouteClient {
        json: include_str!("fixtures/news.json").into(),
        list: include_str!("fixtures/news_list.html").into(),
        entry: include_str!("fixtures/news_entry.html").into(),
        urls: Mutex::new(Vec::new()),
        posts: Mutex::new(Vec::new()),
    });
    let core = RadioCore::with_client(client.clone());
    let articles = core.news(1).unwrap();
    assert_eq!(articles.data[1].id, 81);
    assert!(articles.data[1].text.is_empty());
    let full = core.news_article(81).unwrap();
    assert!(full.text.contains("As always"));
    let comments = core.news_comments(81).unwrap();
    assert_eq!(comments[0].id, 4807);
    let posted = core
        .post_news_comment(81, "hello fags".into())
        .unwrap();
    assert_eq!(posted[0].author, "Anonymous (ab25)");
    let post = &client.posts.lock().expect("posts")[0];
    assert_eq!(post.0, "https://r-a-d.io/news/81");
    assert_eq!(post.1, vec![("comment".into(), "hello fags".into())]);
}

#[test]
fn favorites_empty_nick_skips_http_kethsar_hits_api() {
    let client = Arc::new(UrlClient::new(include_str!("fixtures/faves.json")));
    let core = RadioCore::with_client(client.clone());
    assert!(core.favorites("  ".into(), 1).unwrap().data.is_empty());
    assert!(client.last.lock().expect("lock").is_empty());
    let page = core.favorites("Kethsar".into(), 1).unwrap();
    assert_eq!(page.data[0].tracks_id, Some(6130));
    core.prefetch_favorites("Kethsar".into()).unwrap();
    assert!(core.is_favorite("Kethsar".into(), 6130, String::new()));
    assert!(!core.is_favorite("Kethsar".into(), 0, "not a song".into()));
    assert_eq!(
        *client.last.lock().expect("lock"),
        "https://r-a-d.io/faves?nick=Kethsar&page=1&dl=true"
    );
}

#[test]
fn add_fave_empty_nick_is_noop_without_connect() {
    let client = Arc::new(UrlClient::new(include_str!("fixtures/api_snapshot.json")));
    let core = RadioCore::with_client(client);
    let result = core.add_fave(FaveConfig {
        nick: "  ".into(),
        list_nick: String::new(),
        profile: IrcProfile::Rizon,
        nickserv_password: String::new(),
        bouncer_host: String::new(),
        bouncer_port: 6697,
        bouncer_pass: String::new(),
        allow_insecure_tls: false,
        sasl_username: String::new(),
        sasl_password: String::new(),
        client_cert_pem: String::new(),
        client_key_pem: String::new(),
        tls_fingerprint: String::new(),
    });
    assert_eq!(result.kind, FaveKind::Noop);
}

#[test]
fn probe_irc_empty_nick_does_not_connect() {
    let client = Arc::new(UrlClient::new(include_str!("fixtures/api_snapshot.json")));
    let core = RadioCore::with_client(client);
    let result = core.probe_irc(FaveConfig {
        nick: "  ".into(),
        list_nick: String::new(),
        profile: IrcProfile::Rizon,
        nickserv_password: String::new(),
        bouncer_host: String::new(),
        bouncer_port: 6697,
        bouncer_pass: String::new(),
        allow_insecure_tls: false,
        sasl_username: String::new(),
        sasl_password: String::new(),
        client_cert_pem: String::new(),
        client_key_pem: String::new(),
        tls_fingerprint: String::new(),
    });
    assert_eq!(result.kind, FaveKind::Failed);
    assert_eq!(result.message, "Set a connection nick.");
}

#[test]
fn prefetch_favorites_empty_nick_skips_http() {
    let client = Arc::new(UrlClient::new(include_str!("fixtures/faves.json")));
    let core = RadioCore::with_client(client.clone());
    core.prefetch_favorites("  ".into()).unwrap();
    assert!(client.last.lock().expect("lock").is_empty());
    core.prefetch_favorites("Kethsar".into()).unwrap();
    assert_eq!(
        client.urls.lock().expect("urls").as_slice(),
        ["https://r-a-d.io/faves?nick=Kethsar&page=1&dl=true"]
    );
    core.prefetch_favorites("Kethsar".into()).unwrap();
    assert_eq!(client.urls.lock().expect("urls").len(), 2);
    let again = core.favorites("Kethsar".into(), 1).unwrap();
    assert_eq!(again.data[0].tracks_id, Some(6130));
    assert_eq!(again.last_page, 1);
    assert_eq!(client.urls.lock().expect("urls").len(), 3);
}

struct RouteClient {
    json: String,
    html: String,
    urls: Mutex<Vec<String>>,
}
impl ApiClient for RouteClient {
    fn get(&self, url: &str) -> Result<String, ApiError> {
        self.urls.lock().expect("urls").push(url.to_string());
        if url.contains("dl=true") {
            Ok(self.json.clone())
        } else {
            Ok(self.html.clone())
        }
    }

    fn post_csrf(&self, _url: &str, _token: &str) -> Result<String, ApiError> {
        Err(ApiError::Network {
            detail: "no post".into(),
        })
    }
}

fn hundred_faves_json() -> String {
    let rows: Vec<String> = (0..100)
        .map(|i| {
            format!(
                r#"{{"tracks_id":{i},"meta":"Artist - Title {i}","lastrequested":1,"lastplayed":1,"requestcount":1}}"#
            )
        })
        .collect();
    format!("[{}]", rows.join(","))
}

#[test]
fn favorites_listing_hits_http_every_time() {
    let client = Arc::new(RouteClient {
        json: hundred_faves_json(),
        html: include_str!("fixtures/faves_pagination.html").into(),
        urls: Mutex::new(Vec::new()),
    });
    let core = RadioCore::with_client(client.clone());
    let page = core.favorites("Kethsar".into(), 1).unwrap();
    assert_eq!(page.data.len(), 100);
    assert_eq!(page.last_page, 64);
    assert_eq!(
        client.urls.lock().expect("urls").as_slice(),
        [
            "https://r-a-d.io/faves?nick=Kethsar&page=1&dl=true",
            "https://r-a-d.io/faves?nick=Kethsar",
        ]
    );
    let again = core.favorites("Kethsar".into(), 1).unwrap();
    assert_eq!(again.last_page, 64);
    assert_eq!(client.urls.lock().expect("urls").len(), 4);
}

fn faves_json_ids(ids: impl IntoIterator<Item = i64>) -> String {
    let rows: Vec<String> = ids
        .into_iter()
        .map(|i| {
            format!(
                r#"{{"tracks_id":{i},"meta":"Artist - Title {i}","lastrequested":1,"lastplayed":1,"requestcount":1}}"#
            )
        })
        .collect();
    format!("[{}]", rows.join(","))
}

struct PagedFavesClient {
    pages: std::collections::HashMap<i32, String>,
    html: String,
    urls: Mutex<Vec<String>>,
}
impl ApiClient for PagedFavesClient {
    fn get(&self, url: &str) -> Result<String, ApiError> {
        self.urls.lock().expect("urls").push(url.to_string());
        if url.contains("dl=true") {
            let page = url
                .split("page=")
                .nth(1)
                .and_then(|rest| {
                    rest.chars()
                        .take_while(|c| c.is_ascii_digit())
                        .collect::<String>()
                        .parse()
                        .ok()
                })
                .unwrap_or(1);
            Ok(self
                .pages
                .get(&page)
                .cloned()
                .unwrap_or_else(|| "[]".into()))
        } else {
            Ok(self.html.clone())
        }
    }

    fn post_csrf(&self, _url: &str, _token: &str) -> Result<String, ApiError> {
        Err(ApiError::Network {
            detail: "no post".into(),
        })
    }
}

#[test]
fn favorites_last_page_is_leftover_not_padded_from_previous() {
    let mut pages = std::collections::HashMap::new();
    pages.insert(1, faves_json_ids(1..=100));
    pages.insert(2, faves_json_ids(51..=150));
    let client = Arc::new(PagedFavesClient {
        pages,
        html: r#"<a href="/faves?nick=Kethsar&amp;page=2">2</a>"#.into(),
        urls: Mutex::new(Vec::new()),
    });
    let core = RadioCore::with_client(client.clone());
    let last = core.favorites("Kethsar".into(), 2).unwrap();
    assert_eq!(last.last_page, 2);
    assert_eq!(last.data.len(), 50);
    assert_eq!(last.data[0].tracks_id, Some(101));
    assert_eq!(last.data[49].tracks_id, Some(150));
    let first = core.favorites("Kethsar".into(), 1).unwrap();
    assert_eq!(first.data.len(), 100);
    assert_eq!(first.last_page, 2);
    assert_eq!(first.data[0].tracks_id, Some(1));
}

#[test]
fn favorites_clamped_last_page_is_not_a_duplicate() {
    let mut pages = std::collections::HashMap::new();
    pages.insert(1, faves_json_ids(1..=100));
    pages.insert(2, faves_json_ids(1..=100));
    let client = Arc::new(PagedFavesClient {
        pages,
        html: r#"<a href="/faves?nick=Kethsar&amp;page=2">2</a>"#.into(),
        urls: Mutex::new(Vec::new()),
    });
    let core = RadioCore::with_client(client);
    let last = core.favorites("Kethsar".into(), 2).unwrap();
    assert_eq!(last.last_page, 1);
    assert!(last.data.is_empty());
}

struct FaveRequestClient {
    faves: String,
    csrf: String,
    urls: Mutex<Vec<String>>,
}
impl ApiClient for FaveRequestClient {
    fn get(&self, url: &str) -> Result<String, ApiError> {
        self.urls.lock().expect("urls").push(url.to_string());
        if url.contains("dl=true") {
            Ok(self.faves.clone())
        } else {
            Ok(self.csrf.clone())
        }
    }

    fn post_csrf(&self, url: &str, _token: &str) -> Result<String, ApiError> {
        self.urls.lock().expect("urls").push(url.to_string());
        Ok(include_str!("fixtures/request_success.json").into())
    }
}

#[test]
fn request_random_favorite_empty_nick_skips_http() {
    let client = Arc::new(FaveRequestClient {
        faves: "[]".into(),
        csrf: include_str!("fixtures/csrf_token_comment.html").into(),
        urls: Mutex::new(Vec::new()),
    });
    let core = RadioCore::with_client(client.clone());
    let result = core.request_random_favorite("  ".into()).unwrap();
    assert!(!result.ok);
    assert!(client.urls.lock().expect("urls").is_empty());
}

#[test]
fn request_random_favorite_posts_catalog_id() {
    let client = Arc::new(FaveRequestClient {
        faves: r#"[{"tracks_id":99,"meta":"A - B","lastrequested":null,"lastplayed":null,"requestcount":0}]"#.into(),
        csrf: include_str!("fixtures/csrf_token_comment.html").into(),
        urls: Mutex::new(Vec::new()),
    });
    let core = RadioCore::with_client(client.clone());
    let result = core.request_random_favorite("Geiravor".into()).unwrap();
    assert!(result.ok);
    let urls = client.urls.lock().expect("urls");
    assert!(urls.iter().any(|u| u.contains("nick=Geiravor") && u.contains("dl=true")));
    assert_eq!(urls.last().unwrap(), "https://r-a-d.io/request/99");
}

#[test]
fn request_random_favorite_skips_post_when_none_requestable() {
    let client = Arc::new(FaveRequestClient {
        faves: r#"[{"tracks_id":99,"meta":"A - B","lastrequested":2000000000,"lastplayed":2000000000,"requestcount":0}]"#.into(),
        csrf: include_str!("fixtures/csrf_token_comment.html").into(),
        urls: Mutex::new(Vec::new()),
    });
    let core = RadioCore::with_client(client.clone());
    let result = core.request_random_favorite("Geiravor".into()).unwrap();
    assert!(!result.ok);
    let urls = client.urls.lock().expect("urls");
    assert!(urls.iter().all(|u| !u.contains("/request/")));
}

#[test]
fn request_posts_track_id_after_csrf_bootstrap() {
    let client = Arc::new(UrlClient::new(include_str!(
        "fixtures/csrf_token_comment.html"
    )));
    let core = RadioCore::with_client(client.clone());
    let result = core.request(10136).unwrap();
    assert!(result.ok);
    assert_eq!(
        *client.last.lock().expect("lock"),
        "https://r-a-d.io/request/10136"
    );
}
