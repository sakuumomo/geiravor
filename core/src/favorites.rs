use serde::Deserialize;

use crate::http::ApiError;
use crate::np::split_np;
use crate::request::song_requestable;
use crate::search::encode_path_segment;

pub const FAVES_URL: &str = "https://r-a-d.io/faves";
pub const FAVES_PER_PAGE: i32 = 100;
const FAVES_MAX_PAGE: i32 = 65_536;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FavoritesPage {
    pub current_page: i32,
    pub last_page: i32,
    pub data: Vec<FavoriteRow>,
}

impl FavoritesPage {
    pub fn empty() -> Self {
        Self {
            current_page: 1,
            last_page: 1,
            data: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PageSample {
    pub count: i32,
    pub fingerprint: String,
}

impl PageSample {
    pub fn from_rows(rows: &[FavoriteRow]) -> Self {
        if rows.is_empty() {
            return Self {
                count: 0,
                fingerprint: String::new(),
            };
        }
        let first = &rows[0];
        let last = &rows[rows.len() - 1];
        Self {
            count: rows.len() as i32,
            fingerprint: format!(
                "{}|{:?}|{}|{:?}|{}",
                rows.len(),
                first.tracks_id,
                first.meta,
                last.tracks_id,
                last.meta
            ),
        }
    }

    pub fn from_parts(count: i32, fingerprint: impl Into<String>) -> Self {
        if count <= 0 {
            return Self {
                count: 0,
                fingerprint: String::new(),
            };
        }
        Self {
            count,
            fingerprint: fingerprint.into(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FavoriteRow {
    pub tracks_id: Option<i64>,
    pub meta: String,
    pub artist: String,
    pub title: String,
    pub last_requested: Option<i64>,
    pub last_played: Option<i64>,
    pub request_count: Option<i64>,
}

pub fn row_is_song(row: &FavoriteRow, track_id: i64, np: &str) -> bool {
    if track_id > 0 && row.tracks_id == Some(track_id) {
        return true;
    }
    let left = np
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .to_lowercase();
    let right = row
        .meta
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .to_lowercase();
    !left.is_empty() && left == right
}

/// Catalog favorites that pass the same cooldown as a row Request. `slot` picks uniformly.
pub fn pick_requestable_id(rows: &[FavoriteRow], now: i64, slot: u64) -> Option<i64> {
    let ids: Vec<i64> = rows
        .iter()
        .filter_map(|row| {
            let id = row.tracks_id.filter(|id| *id > 0)?;
            if song_requestable(
                row.last_played,
                row.last_requested,
                row.request_count,
                now,
            ) {
                Some(id)
            } else {
                None
            }
        })
        .collect();
    if ids.is_empty() {
        None
    } else {
        Some(ids[(slot as usize) % ids.len()])
    }
}

#[derive(Deserialize)]
struct RawFavorite {
    tracks_id: Option<i64>,
    #[serde(default)]
    meta: String,
    lastrequested: Option<i64>,
    lastplayed: Option<i64>,
    requestcount: Option<i64>,
}

pub fn faves_url(nick: &str, page: i32) -> String {
    format!(
        "{FAVES_URL}?nick={}&page={}&dl=true",
        encode_path_segment(nick),
        page.max(1)
    )
}

/// HTML list (pagination). JSON dump is [`faves_url`].
pub fn faves_html_url(nick: &str) -> String {
    format!("{FAVES_URL}?nick={}", encode_path_segment(nick))
}

fn page_query_value(href: &str) -> Option<i32> {
    let rest = href.split("page=").nth(1)?;
    let digits: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
    digits.parse().ok().filter(|n| *n > 0)
}

/// Max `page=` on `/faves?` hrefs. Ignore `/v1/request?...page=`.
pub fn parse_faves_last_page(html: &str) -> i32 {
    let mut last = 1i32;
    let mut from = 0usize;
    while let Some(rel) = html[from..].find("/faves?") {
        let start = from + rel;
        let tail = &html[start..];
        let end = tail
            .find(|c: char| c == '"' || c == '\'' || c == ' ' || c == '<' || c == '>')
            .unwrap_or(tail.len());
        let href = tail[..end].replace("&amp;", "&");
        if let Some(page) = page_query_value(&href) {
            last = last.max(page);
        }
        from = start + 1;
    }
    last
}

/// Live JSON **clamps** past-last pages to the last page. Compare fingerprints.
pub fn discover_last_page(full_page: i32, fetch: impl Fn(i32) -> PageSample) -> i32 {
    let start = full_page.max(1);
    let mut unique_lo = start;
    let mut prev = start;
    let mut prev_sample = fetch(start);
    if prev_sample.count == 0 {
        return 1;
    }
    let mut hi = (start * 2).min(FAVES_MAX_PAGE);
    loop {
        let sample = fetch(hi);
        if sample.count == 0 {
            return last_non_empty(prev, hi, &fetch);
        }
        if sample.fingerprint == prev_sample.fingerprint {
            return first_with_fingerprint(unique_lo, prev, &sample.fingerprint, &fetch);
        }
        unique_lo = prev;
        prev = hi;
        prev_sample = sample;
        let next = hi.saturating_mul(2);
        if next > FAVES_MAX_PAGE || next <= hi {
            let tail = fetch((hi + 1).min(FAVES_MAX_PAGE));
            if tail.count == 0 {
                return hi;
            }
            if tail.fingerprint == prev_sample.fingerprint {
                return first_with_fingerprint(unique_lo, hi, &prev_sample.fingerprint, &fetch);
            }
            return hi;
        }
        hi = next;
    }
}

fn first_with_fingerprint(
    lo: i32,
    hi: i32,
    fp: &str,
    fetch: &impl Fn(i32) -> PageSample,
) -> i32 {
    let mut left = lo;
    let mut right = hi;
    let mut found = hi;
    while left <= right {
        let mid = left + (right - left) / 2;
        let sample = fetch(mid);
        if sample.fingerprint == fp {
            found = mid;
            right = mid - 1;
        } else {
            left = mid + 1;
        }
    }
    found
}

fn last_non_empty(lo: i32, empty_hi: i32, fetch: &impl Fn(i32) -> PageSample) -> i32 {
    let mut left = lo;
    let mut right = empty_hi - 1;
    let mut last = lo;
    while left <= right {
        let mid = left + (right - left) / 2;
        let sample = fetch(mid);
        if sample.count == 0 {
            right = mid - 1;
        } else if sample.count < FAVES_PER_PAGE {
            return mid;
        } else {
            last = mid;
            left = mid + 1;
        }
    }
    last
}

pub fn parse_faves(json: &str) -> Result<Vec<FavoriteRow>, ApiError> {
    let raw: Vec<RawFavorite> = serde_json::from_str(json).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    Ok(raw
        .into_iter()
        .map(|row| {
            let (artist, title) = split_np(&row.meta);
            FavoriteRow {
                tracks_id: row.tracks_id,
                meta: row.meta,
                artist,
                title,
                last_requested: row.lastrequested,
                last_played: row.lastplayed,
                request_count: row.requestcount,
            }
        })
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    // Kethsar is only the captured GET /faves fixture nick (a long public list).
    // Do not reuse it for IRC add-fave or other tests.

    #[test]
    fn faves_url_encodes_captured_fixture_nick() {
        assert_eq!(
            faves_url("Kethsar", 1),
            "https://r-a-d.io/faves?nick=Kethsar&page=1&dl=true"
        );
        assert_eq!(
            faves_url("Kethsar", 3),
            "https://r-a-d.io/faves?nick=Kethsar&page=3&dl=true"
        );
    }

    #[test]
    fn row_is_song_matches_track_id_or_meta() {
        let row = FavoriteRow {
            tracks_id: Some(42),
            meta: "Hirasawa Susumu - Gats".into(),
            artist: "Hirasawa Susumu".into(),
            title: "Gats".into(),
            last_requested: None,
            last_played: None,
            request_count: None,
        };
        assert!(row_is_song(&row, 42, "other"));
        assert!(row_is_song(&row, 0, "hirasawa  susumu - gats"));
        assert!(!row_is_song(&row, 7, "Someone - Else"));
    }

    fn catalog_row(id: i64, last_played: Option<i64>) -> FavoriteRow {
        FavoriteRow {
            tracks_id: Some(id),
            meta: "A - B".into(),
            artist: "A".into(),
            title: "B".into(),
            last_requested: None,
            last_played,
            request_count: Some(0),
        }
    }

    #[test]
    fn pick_requestable_skips_null_id_and_cooldown() {
        let now = 1_700_000_000;
        let delay = crate::request::request_delay_secs(0);
        let rows = vec![
            FavoriteRow {
                tracks_id: None,
                meta: "DJ - Only".into(),
                artist: "DJ".into(),
                title: "Only".into(),
                last_requested: None,
                last_played: None,
                request_count: None,
            },
            catalog_row(1, Some(now - 10)),
            catalog_row(2, Some(now - delay)),
            catalog_row(3, None),
        ];
        assert_eq!(pick_requestable_id(&rows, now, 0), Some(2));
        assert_eq!(pick_requestable_id(&rows, now, 1), Some(3));
        assert_eq!(pick_requestable_id(&rows, now, 2), Some(2));
        assert_eq!(pick_requestable_id(&[], now, 0), None);
    }

    #[test]
    fn parse_kethsar_fixture_allows_null_track_id() {
        let rows = parse_faves(include_str!("../tests/fixtures/faves.json")).unwrap();
        assert_eq!(rows[0].tracks_id, Some(6130));
        assert_eq!(rows[0].artist, "Mori Yuuya");
        assert_eq!(rows[0].title, "Seitokai Yakuindomo no March");
        assert_eq!(rows[0].request_count, Some(4));
        assert_eq!(rows[1].tracks_id, None);
        assert_eq!(rows[1].artist, "Masayoshi Minoshima");
        assert_eq!(rows[1].last_requested, None);
    }

    #[test]
    fn unknown_nick_is_empty_list() {
        assert!(
            parse_faves(include_str!("../tests/fixtures/faves_empty.json"))
                .unwrap()
                .is_empty()
        );
    }

    #[test]
    fn faves_html_url_is_not_the_json_dump() {
        assert_eq!(
            faves_html_url("Kethsar"),
            "https://r-a-d.io/faves?nick=Kethsar"
        );
    }

    #[test]
    fn parse_faves_last_page_uses_pagination_not_request_forms() {
        assert_eq!(
            parse_faves_last_page(include_str!("../tests/fixtures/faves_pagination.html")),
            64
        );
        assert_eq!(parse_faves_last_page("<p>no pager</p>"), 1);
    }

    fn empty_past(page: i32, counts: &[(i32, i32)]) -> PageSample {
        match counts.iter().find(|(p, _)| *p == page) {
            Some((_, count)) if *count > 0 => PageSample::from_parts(*count, format!("e-{page}")),
            _ => PageSample::from_parts(0, ""),
        }
    }

    fn clamp_to(page: i32, last: i32, last_count: i32) -> PageSample {
        let clamped = page.max(1).min(last);
        let count = if clamped == last { last_count } else { 100 };
        PageSample::from_parts(count, format!("c-{clamped}-{count}"))
    }

    #[test]
    fn discover_last_when_api_clamps_to_full_last_page() {
        assert_eq!(
            discover_last_page(1, |p| clamp_to(p, 64, 100)),
            64
        );
    }

    #[test]
    fn discover_last_when_api_clamps_to_short_last_page() {
        assert_eq!(discover_last_page(1, |p| clamp_to(p, 7, 37)), 7);
    }

    #[test]
    fn discover_last_when_empty_past_last() {
        let counts = [(1, 100), (2, 100), (3, 100), (4, 100), (5, 100)];
        assert_eq!(discover_last_page(1, |p| empty_past(p, &counts)), 5);
    }

    #[test]
    fn discover_last_when_page_one_is_only_full_page() {
        assert_eq!(
            discover_last_page(1, |p| empty_past(p, &[(1, 100)])),
            1
        );
    }
}
