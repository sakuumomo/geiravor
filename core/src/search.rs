//! Search JSON, can-request, and request POST body. See `docs/spec/requests-faves.md`.

use serde::Deserialize;

use crate::error::ApiError;
use crate::html::path_encode;
use crate::parse::check_bound;

pub const SEARCH_BASE: &str = "https://r-a-d.io/api/search/";
pub const CAN_REQUEST_URL: &str = "https://r-a-d.io/api/can-request";
pub const SEARCH_HTML_URL: &str = "https://r-a-d.io/search";
pub const REQUEST_BASE: &str = "https://r-a-d.io/request/";

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchTrack {
    pub artist: String,
    pub title: String,
    pub id: i64,
    pub lastplayed: i64,
    pub lastrequested: i64,
    pub requestable: bool,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchPage {
    pub total: u32,
    pub per_page: u32,
    pub current_page: u32,
    pub last_page: u32,
    pub tracks: Vec<SearchTrack>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RequestResult {
    pub ok: bool,
    pub text: String,
}

#[derive(Deserialize)]
struct RawSearch {
    #[serde(default)]
    total: u32,
    #[serde(default)]
    per_page: u32,
    #[serde(default)]
    current_page: u32,
    #[serde(default)]
    last_page: u32,
    #[serde(default)]
    data: Vec<RawTrack>,
}

#[derive(Deserialize)]
struct RawTrack {
    #[serde(default)]
    artist: String,
    #[serde(default)]
    title: String,
    #[serde(default)]
    id: i64,
    #[serde(default)]
    lastplayed: i64,
    #[serde(default)]
    lastrequested: i64,
    #[serde(default)]
    requestable: bool,
}

#[derive(Deserialize)]
struct RawCan {
    #[serde(rename = "Main")]
    main: RawCanMain,
}

#[derive(Deserialize)]
struct RawCanMain {
    #[serde(default)]
    requests: bool,
}

#[derive(Deserialize)]
struct RawReq {
    #[serde(default)]
    success: Option<String>,
    #[serde(default)]
    error: Option<String>,
}

pub fn search_url(query: &str, page: u32) -> String {
    let page = page.max(1);
    format!("{SEARCH_BASE}{}?page={page}", path_encode(query.trim()))
}

pub fn request_url(id: i64) -> String {
    format!("{REQUEST_BASE}{id}")
}

pub fn parse_search(bytes: &[u8]) -> Result<SearchPage, ApiError> {
    check_bound(bytes)?;
    let raw: RawSearch = serde_json::from_slice(bytes).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    if raw.total == 0 && raw.data.is_empty() {
        return Ok(SearchPage {
            total: 0,
            per_page: raw.per_page,
            current_page: raw.current_page,
            last_page: raw.last_page.max(1),
            tracks: Vec::new(),
        });
    }
    Ok(SearchPage {
        total: raw.total,
        per_page: raw.per_page.max(1),
        current_page: raw.current_page.max(1),
        last_page: raw.last_page.max(1),
        tracks: raw
            .data
            .into_iter()
            .map(|t| SearchTrack {
                artist: t.artist,
                title: t.title,
                id: t.id,
                lastplayed: t.lastplayed,
                lastrequested: t.lastrequested,
                requestable: t.requestable,
            })
            .collect(),
    })
}

/// Capital `Main`. Do not reuse the `/api` lowercase decoder.
pub fn parse_can_request(bytes: &[u8]) -> Result<bool, ApiError> {
    check_bound(bytes)?;
    let raw: RawCan = serde_json::from_slice(bytes).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    Ok(raw.main.requests)
}

pub fn parse_request_body(bytes: &[u8]) -> Result<RequestResult, ApiError> {
    check_bound(bytes)?;
    let raw: RawReq = serde_json::from_slice(bytes).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    if let Some(text) = raw.success {
        return Ok(RequestResult { ok: true, text });
    }
    if let Some(text) = raw.error {
        return Ok(RequestResult { ok: false, text });
    }
    Err(ApiError::Decode {
        detail: "request: no success or error".into(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn search_gats_page() {
        let p = parse_search(include_bytes!("../tests/fixtures/search_gats.json")).unwrap();
        assert_eq!(p.total, 49);
        assert_eq!(p.per_page, 20);
        assert_eq!(p.last_page, 3);
        assert_eq!(p.tracks[0].artist, "Hirasawa Susumu");
        assert_eq!(p.tracks[0].title, "Gats");
        assert_eq!(p.tracks[0].id, 15358);
        assert!(p.tracks[0].requestable);
    }

    #[test]
    fn search_url_encodes_path() {
        assert_eq!(
            search_url("a b", 1),
            "https://r-a-d.io/api/search/a%20b?page=1"
        );
    }

    #[test]
    fn can_request_capital_main() {
        assert!(parse_can_request(include_bytes!("../tests/fixtures/can_request.json")).unwrap());
        assert!(parse_can_request(br#"{"main":{"requests":true}}"#).is_err());
    }

    #[test]
    fn request_success_and_error() {
        let ok = parse_request_body(include_bytes!("../tests/fixtures/request_ok.json")).unwrap();
        assert!(ok.ok);
        let err = parse_request_body(include_bytes!("../tests/fixtures/request_err.json")).unwrap();
        assert!(!err.ok);
        assert!(err.text.contains("wait"));
    }
}
