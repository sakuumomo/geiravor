//! Public `/faves` JSON + HTML last page. See `docs/spec/requests-faves.md`.

use serde::Deserialize;

use crate::error::ApiError;
use crate::html::path_encode;
use crate::parse::{check_bound, split_np};

pub const FAVES_URL: &str = "https://r-a-d.io/faves";

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct FaveRow {
    pub tracks_id: i64,
    pub artist: String,
    pub title: String,
    pub lastrequested: i64,
    pub lastplayed: i64,
    pub requestcount: i64,
}

#[derive(Deserialize)]
struct RawFave {
    #[serde(default)]
    tracks_id: Option<i64>,
    #[serde(default)]
    meta: Option<String>,
    #[serde(default)]
    lastrequested: Option<i64>,
    #[serde(default)]
    lastplayed: Option<i64>,
    #[serde(default)]
    requestcount: Option<i64>,
}

pub fn faves_json_url(nick: &str, page: u32) -> String {
    format!(
        "{FAVES_URL}?nick={}&page={}&dl=true",
        path_encode(nick.trim()),
        page.max(1)
    )
}

pub fn faves_html_url(nick: &str) -> String {
    format!("{FAVES_URL}?nick={}", path_encode(nick.trim()))
}

pub fn parse_faves_json(bytes: &[u8]) -> Result<Vec<FaveRow>, ApiError> {
    check_bound(bytes)?;
    let raw: Vec<RawFave> = serde_json::from_slice(bytes).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    Ok(raw
        .into_iter()
        .map(|r| {
            let meta = r.meta.unwrap_or_default();
            let (artist, title) = split_np(&meta);
            FaveRow {
                tracks_id: r.tracks_id.unwrap_or(0),
                artist,
                title,
                lastrequested: r.lastrequested.unwrap_or(0),
                lastplayed: r.lastplayed.unwrap_or(0),
                requestcount: r.requestcount.unwrap_or(0),
            }
        })
        .collect())
}

/// Max `/faves?…page=` in HTML. Ignore `/v1/request`.
pub fn faves_html_last_page(html: &str) -> u32 {
    if crate::parse::check_bound(html.as_bytes()).is_err() {
        return 1;
    }
    let mut max = 1u32;
    let mut rest = html;
    while let Some(i) = rest.find("/faves?") {
        rest = &rest[i + 7..];
        if let Some(p) = rest.find("page=") {
            if p > 80 {
                continue;
            }
            let digits = rest[p + 5..]
                .chars()
                .take_while(|c| c.is_ascii_digit())
                .collect::<String>();
            if let Ok(n) = digits.parse::<u32>() {
                max = max.max(n);
            }
        }
    }
    max
}

/// Drop rows already on the previous JSON page. Empty leftover means clamp.
pub fn trim_faves_overlap(prev: &[FaveRow], last: Vec<FaveRow>) -> Vec<FaveRow> {
    last.into_iter()
        .filter(|r| {
            !prev
                .iter()
                .any(|p| p.tracks_id == r.tracks_id && r.tracks_id != 0)
        })
        .collect()
}

/// Faves JSON has no `requestable`. Gray Request using lastrequested vs now.
#[uniffi::export]
pub fn fave_requestable(lastrequested: i64, now: i64) -> bool {
    lastrequested <= 0 || now.saturating_sub(lastrequested) >= 2 * 3600
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn json_splits_meta_and_nulls() {
        let rows = parse_faves_json(include_bytes!("../tests/fixtures/faves_page.json")).unwrap();
        assert_eq!(rows.len(), 2);
        assert_eq!(rows[0].tracks_id, 15358);
        assert_eq!(rows[0].artist, "Hirasawa Susumu");
        assert_eq!(rows[1].tracks_id, 1);
        assert_eq!(rows[1].lastrequested, 0);
    }

    #[test]
    fn unknown_nick_is_empty_not_error() {
        let rows = parse_faves_json(b"[]").unwrap();
        assert!(rows.is_empty());
    }

    #[test]
    fn html_last_page_ignores_v1_request() {
        let html = include_str!("../tests/fixtures/faves_pager.html");
        assert_eq!(faves_html_last_page(html), 5);
    }

    #[test]
    fn leftover_drops_overlap() {
        let prev = parse_faves_json(include_bytes!("../tests/fixtures/faves_page.json")).unwrap();
        let last =
            parse_faves_json(include_bytes!("../tests/fixtures/faves_overlap.json")).unwrap();
        let trimmed = trim_faves_overlap(&prev, last);
        assert_eq!(trimmed.len(), 1);
        assert_eq!(trimmed[0].tracks_id, 99);
    }
}
