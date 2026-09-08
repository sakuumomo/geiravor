//! JSON/HTML parse. This module must not depend on reqwest.
//!
//! Product rules: [`docs/spec/api.md`](../../docs/spec/api.md).

use serde::Deserialize;

use crate::error::ApiError;

/// Reject bodies larger than this (bytes). Hardened; not “read until OOM.”
pub const MAX_BODY: usize = 2 * 1024 * 1024;

pub const API_URL: &str = "https://r-a-d.io/api";
pub const STREAM_URL: &str = "https://stream.r-a-d.io/main.mp3";
pub const DJ_IMAGE_BASE: &str = "https://r-a-d.io/api/dj-image/";
pub const HOME_URL: &str = "https://r-a-d.io/";

/// Snapshot used by the UI, Auto, and fave. Extra JSON keys are ignored.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Status {
    pub np: String,
    pub artist: String,
    pub title: String,
    pub listeners: u32,
    pub is_afk: bool,
    pub current: i64,
    pub start_time: i64,
    pub end_time: i64,
    pub track_id: i64,
    pub thread: String,
    pub requesting: bool,
    pub dj: Dj,
    pub queue: Vec<ListEntry>,
    pub lp: Vec<ListEntry>,
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Dj {
    pub id: i64,
    pub name: String,
    pub image: String,
}

#[derive(Debug, Clone, PartialEq, serde::Serialize, uniffi::Record)]
pub struct ListEntry {
    pub artist: String,
    pub title: String,
    pub timestamp: i64,
    pub is_request: bool,
}

/// AFK song window. Live DJ has no track clock.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SongProgress {
    pub elapsed_secs: i64,
    pub duration_secs: i64,
    pub known: bool,
}

#[derive(Deserialize)]
struct Envelope {
    main: RawMain,
}

#[derive(Deserialize)]
struct RawMain {
    np: String,
    listeners: u32,
    isafkstream: bool,
    current: i64,
    start_time: i64,
    end_time: i64,
    trackid: i64,
    thread: String,
    requesting: bool,
    dj: RawDj,
    #[serde(default)]
    queue: Vec<RawList>,
    #[serde(default)]
    lp: Vec<RawList>,
    #[serde(default)]
    tags: Option<Vec<String>>,
}

#[derive(Deserialize)]
struct RawDj {
    id: i64,
    djname: String,
    djimage: String,
}

#[derive(Deserialize)]
struct RawList {
    meta: String,
    timestamp: i64,
    #[serde(rename = "type")]
    kind: i64,
}

/// Fail if `bytes` exceeds [`MAX_BODY`].
pub fn check_bound(bytes: &[u8]) -> Result<(), ApiError> {
    if bytes.len() > MAX_BODY {
        return Err(ApiError::Decode {
            detail: format!("body {} exceeds {MAX_BODY}", bytes.len()),
        });
    }
    Ok(())
}

/// Parse `GET /api` JSON. `tags: null` is empty, not a decode failure.
pub fn parse_status(bytes: &[u8]) -> Result<Status, ApiError> {
    check_bound(bytes)?;
    let text = std::str::from_utf8(bytes).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    parse_status_str(text)
}

/// Parse a UTF-8 `/api` body.
pub fn parse_status_str(text: &str) -> Result<Status, ApiError> {
    if text.len() > MAX_BODY {
        return Err(ApiError::Decode {
            detail: format!("body {} exceeds {MAX_BODY}", text.len()),
        });
    }
    let env: Envelope = serde_json::from_str(text).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    let m = env.main;
    let (artist, title) = split_np(&m.np);
    let queue: Vec<ListEntry> = m.queue.into_iter().take(5).map(list_entry).collect();
    let lp: Vec<ListEntry> = m.lp.into_iter().take(5).map(list_entry).collect();
    Ok(Status {
        np: m.np,
        artist,
        title,
        listeners: m.listeners,
        is_afk: m.isafkstream,
        current: m.current,
        start_time: m.start_time,
        end_time: m.end_time,
        track_id: m.trackid,
        thread: m.thread,
        requesting: m.requesting,
        dj: Dj {
            id: m.dj.id,
            name: m.dj.djname,
            image: m.dj.djimage,
        },
        queue,
        lp,
        tags: m.tags.unwrap_or_default(),
    })
}

fn list_entry(raw: RawList) -> ListEntry {
    let (artist, title) = split_np(&raw.meta);
    ListEntry {
        artist,
        title,
        timestamp: raw.timestamp,
        is_request: raw.kind == 1,
    }
}

/// Split on the first `" - "`. No separator → empty artist, full title.
pub fn split_np(np: &str) -> (String, String) {
    match np.split_once(" - ") {
        Some((a, t)) => (a.trim().to_string(), t.trim().to_string()),
        None => (String::new(), np.trim().to_string()),
    }
}

/// DJ image URL, or `None` if `image` is blank.
pub fn dj_image_url(image: &str) -> Option<String> {
    let image = image.trim();
    if image.is_empty() {
        None
    } else {
        Some(format!("{DJ_IMAGE_BASE}{image}"))
    }
}

/// Thread URL for phone now-playing. Hidden on Hanyuu or `none`/empty.
pub fn thread_visible(is_afk: bool, thread: &str) -> bool {
    if is_afk {
        return false;
    }
    let t = thread.trim();
    !t.is_empty() && t != "none"
}

fn thread_url(thread: &str) -> String {
    let t = thread.trim();
    if t.len() >= 6 && t[..6].eq_ignore_ascii_case("image:") {
        t[6..].trim().to_string()
    } else {
        t.to_string()
    }
}

fn thread_is_media(url: &str) -> bool {
    let path = url
        .split(['?', '#'])
        .next()
        .unwrap_or(url)
        .to_ascii_lowercase();
    path.ends_with(".gif")
        || path.ends_with(".png")
        || path.ends_with(".jpg")
        || path.ends_with(".jpeg")
        || path.ends_with(".webp")
        || path.ends_with(".mp4")
        || path.ends_with(".webm")
}

/// Embed URL (`image:` or media path). Empty if this thread is a browser link or hidden.
pub fn thread_embed_url(is_afk: bool, thread: &str) -> String {
    if !thread_visible(is_afk, thread) {
        return String::new();
    }
    let url = thread_url(thread);
    if thread_is_media(&url) {
        url
    } else {
        String::new()
    }
}

/// Browser link. Empty if this thread is an embed or hidden.
pub fn thread_link_url(is_afk: bool, thread: &str) -> String {
    if !thread_visible(is_afk, thread) {
        return String::new();
    }
    let url = thread_url(thread);
    if thread_is_media(&url) {
        return String::new();
    }
    if url.starts_with("https://") || url.starts_with("http://") {
        url
    } else {
        String::new()
    }
}

/// AFK progress. Live DJ or `end <= start` → unknown.
pub fn song_progress(status: &Status, local_now_secs: i64, local_at_fetch: i64) -> SongProgress {
    if !status.is_afk {
        return SongProgress {
            elapsed_secs: 0,
            duration_secs: 0,
            known: false,
        };
    }
    let duration = status.end_time - status.start_time;
    if duration <= 0 {
        return SongProgress {
            elapsed_secs: 0,
            duration_secs: 0,
            known: false,
        };
    }
    let elapsed = (local_now_secs + (status.current - local_at_fetch)) - status.start_time;
    let elapsed = elapsed.clamp(0, duration);
    SongProgress {
        elapsed_secs: elapsed,
        duration_secs: duration,
        known: true,
    }
}

/// Last-played relative label vs snapshot `current`.
#[uniffi::export]
pub fn relative_last_played(current: i64, timestamp: i64) -> String {
    let mins = (current - timestamp) / 60;
    if mins < 1 {
        "<1 minute ago".into()
    } else if mins == 1 {
        "1 minute ago".into()
    } else {
        format!("{mins} minutes ago")
    }
}

/// Queue relative label vs snapshot `current`.
#[uniffi::export]
pub fn relative_queue(current: i64, timestamp: i64) -> String {
    let mins = (timestamp - current) / 60;
    if mins <= 0 {
        "in <1 minute".into()
    } else if mins == 1 {
        "in 1 minute".into()
    } else {
        format!("in {mins} minutes")
    }
}

/// Theme pack name from `href="/assets/{name}/css/…"`. First stylesheet wins.
pub fn parse_theme_name(html: &str) -> Option<String> {
    check_bound(html.as_bytes()).ok()?;
    let marker = "/assets/";
    let css = "/css/";
    let i = html.find(marker)?;
    let rest = &html[i + marker.len()..];
    let j = rest.find(css)?;
    let name = &rest[..j];
    if name.is_empty() || name.contains('/') {
        None
    } else {
        Some(name.to_string())
    }
}

/// Last-paint chrome: ignore `current` and `listeners` (they tick every poll).
pub fn last_paint_chrome(status: &Status) -> String {
    serde_json::json!({
        "np": status.np,
        "is_afk": status.is_afk,
        "track_id": status.track_id,
        "thread": status.thread,
        "requesting": status.requesting,
        "dj": { "id": status.dj.id, "name": status.dj.name, "image": status.dj.image },
        "queue": status.queue,
        "lp": status.lp,
        "tags": status.tags,
    })
    .to_string()
}

#[uniffi::export]
pub fn song_progress_at(status: Status, local_now_secs: i64, local_at_fetch: i64) -> SongProgress {
    song_progress(&status, local_now_secs, local_at_fetch)
}

#[uniffi::export]
pub fn thread_is_visible(is_afk: bool, thread: String) -> bool {
    thread_visible(is_afk, &thread)
}

#[uniffi::export]
pub fn thread_embed_url_for(is_afk: bool, thread: String) -> String {
    thread_embed_url(is_afk, &thread)
}

#[uniffi::export]
pub fn thread_link_url_for(is_afk: bool, thread: String) -> String {
    thread_link_url(is_afk, &thread)
}

#[uniffi::export]
pub fn format_clock(secs: i64) -> String {
    let secs = secs.max(0);
    format!("{}:{:02}", secs / 60, secs % 60)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn split_examples() {
        assert_eq!(
            split_np("Aimer with chelly (EGOIST) - ninelie"),
            ("Aimer with chelly (EGOIST)".into(), "ninelie".into())
        );
        assert_eq!(
            split_np("3L - ・－・・ －－－ ・・・－ ・"),
            ("3L".into(), "・－・・ －－－ ・・・－ ・".into())
        );
        assert_eq!(split_np("no hyphen"), ("".into(), "no hyphen".into()));
    }

    #[test]
    fn bound_rejects_huge() {
        let big = vec![b'x'; MAX_BODY + 1];
        assert!(check_bound(&big).is_err());
    }

    #[test]
    fn theme_name_from_assets() {
        let html = r#"<link href="/assets/christmas/css/bulma.min.css">"#;
        assert_eq!(parse_theme_name(html).as_deref(), Some("christmas"));
    }

    #[test]
    fn blank_dj_image_is_none() {
        assert!(dj_image_url("  ").is_none());
        assert_eq!(
            dj_image_url("18-e0177611a37081b5.png").as_deref(),
            Some("https://r-a-d.io/api/dj-image/18-e0177611a37081b5.png")
        );
    }

    #[test]
    fn thread_image_prefix_is_embed() {
        assert_eq!(
            thread_embed_url(false, "image:https://static.r-a-d.io/x.gif"),
            "https://static.r-a-d.io/x.gif"
        );
        assert!(thread_link_url(false, "image:https://static.r-a-d.io/x.gif").is_empty());
    }

    #[test]
    fn thread_http_page_is_browser_link() {
        let u = "https://boards.4chan.org/a/thread/1";
        assert!(thread_embed_url(false, u).is_empty());
        assert_eq!(thread_link_url(false, u), u);
        assert!(thread_embed_url(true, u).is_empty());
        assert!(thread_link_url(true, u).is_empty());
    }

    #[test]
    fn clock_mm_ss() {
        assert_eq!(format_clock(0), "0:00");
        assert_eq!(format_clock(125), "2:05");
    }
}
