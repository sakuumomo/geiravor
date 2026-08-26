use serde::Deserialize;

use crate::http::ApiError;

pub const SEARCH_URL: &str = "https://r-a-d.io/api/search/";
pub const CAN_REQUEST_URL: &str = "https://r-a-d.io/api/can-request";
pub const REQUEST_URL: &str = "https://r-a-d.io/request/";

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SearchHit {
    pub artist: String,
    pub title: String,
    pub id: i64,
    pub last_played: i64,
    pub last_requested: i64,
    pub requestable: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SearchPage {
    pub total: i64,
    pub per_page: i64,
    pub current_page: i64,
    pub last_page: i64,
    pub from: i64,
    pub to: i64,
    pub data: Vec<SearchHit>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RequestResult {
    pub ok: bool,
    pub message: String,
}

impl SearchPage {
    pub fn empty() -> Self {
        Self {
            total: 0,
            per_page: 20,
            current_page: 1,
            last_page: 1,
            from: 0,
            to: 0,
            data: Vec::new(),
        }
    }
}

pub fn encode_path_segment(input: &str) -> String {
    let mut out = String::new();
    for b in input.as_bytes() {
        match *b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'.' | b'_' | b'~' => {
                out.push(*b as char);
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

pub fn search_url(query: &str, page: i32) -> String {
    format!(
        "{SEARCH_URL}{}?page={}",
        encode_path_segment(query),
        page.max(1)
    )
}

pub fn request_url(track_id: i64) -> String {
    format!("{REQUEST_URL}{track_id}")
}

#[derive(Deserialize)]
struct RawSearchPage {
    total: i64,
    per_page: i64,
    current_page: i64,
    last_page: i64,
    from: i64,
    to: i64,
    data: Vec<RawSearchHit>,
}

#[derive(Deserialize)]
struct RawSearchHit {
    artist: String,
    title: String,
    id: i64,
    #[serde(default)]
    lastplayed: i64,
    #[serde(default)]
    lastrequested: i64,
    requestable: bool,
}

#[derive(Deserialize)]
struct RawCanRequest {
    #[serde(rename = "Main")]
    main: RawCanRequestMain,
}

#[derive(Deserialize)]
struct RawCanRequestMain {
    requests: bool,
}

#[derive(Deserialize)]
struct RawRequestResult {
    success: Option<String>,
    error: Option<String>,
}

pub fn parse_search(json: &str) -> Result<SearchPage, ApiError> {
    let raw: RawSearchPage = serde_json::from_str(json).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    Ok(SearchPage {
        total: raw.total,
        per_page: raw.per_page,
        current_page: raw.current_page,
        last_page: raw.last_page,
        from: raw.from,
        to: raw.to,
        data: raw
            .data
            .into_iter()
            .map(|hit| SearchHit {
                artist: hit.artist,
                title: hit.title,
                id: hit.id,
                last_played: hit.lastplayed,
                last_requested: hit.lastrequested,
                requestable: hit.requestable,
            })
            .collect(),
    })
}

pub fn parse_can_request(json: &str) -> Result<bool, ApiError> {
    let raw: RawCanRequest = serde_json::from_str(json).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    Ok(raw.main.requests)
}

pub fn parse_request_result(json: &str) -> Result<RequestResult, ApiError> {
    let raw: RawRequestResult = serde_json::from_str(json).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    if let Some(message) = raw.success {
        return Ok(RequestResult { ok: true, message });
    }
    if let Some(message) = raw.error {
        return Ok(RequestResult { ok: false, message });
    }
    Err(ApiError::Decode {
        detail: "request response missing success/error".into(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn search_url_percent_encodes_path() {
        assert_eq!(
            search_url("Aimer with chelly (EGOIST)", 1),
            "https://r-a-d.io/api/search/Aimer%20with%20chelly%20%28EGOIST%29?page=1"
        );
        assert_eq!(search_url("3L", 0), "https://r-a-d.io/api/search/3L?page=1");
    }

    #[test]
    fn parse_search_fixture_does_not_split_np() {
        let page = parse_search(include_str!("../tests/fixtures/search_page.json")).unwrap();
        assert_eq!(page.total, 47);
        assert_eq!(page.data[0].artist, "Aimer");
        assert_eq!(page.data[0].title, "Brave Shine");
        assert_eq!(page.data[0].id, 10136);
        assert!(page.data[0].requestable);
        assert!(!page.data[1].requestable);
        assert_eq!(page.data[1].artist, "Aimer with chelly (EGOIST)");
    }

    #[test]
    fn parse_empty_search() {
        let page = parse_search(include_str!("../tests/fixtures/search_empty.json")).unwrap();
        assert!(page.data.is_empty());
        assert_eq!(page.total, 0);
    }

    #[test]
    fn can_request_uses_capital_main() {
        assert!(parse_can_request(include_str!("../tests/fixtures/can_request.json")).unwrap());
        assert!(
            !parse_can_request(include_str!("../tests/fixtures/can_request_false.json")).unwrap()
        );
        assert!(parse_can_request(r#"{"main":{"requests":true}}"#).is_err());
    }

    #[test]
    fn request_result_success_and_error() {
        let ok =
            parse_request_result(include_str!("../tests/fixtures/request_success.json")).unwrap();
        assert!(ok.ok);
        assert!(ok.message.contains("request"));
        let err =
            parse_request_result(include_str!("../tests/fixtures/request_error.json")).unwrap();
        assert!(!err.ok);
        assert!(err.message.contains("can't request"));
    }
}
