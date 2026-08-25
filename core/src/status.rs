use serde::Deserialize;

use crate::np::split_np;

pub const API_URL: &str = "https://r-a-d.io/api";
pub const STREAM_URL: &str = "https://stream.r-a-d.io/main.mp3";
pub const DJ_IMAGE_BASE: &str = "https://r-a-d.io/api/dj-image/";

#[uniffi::export]
pub fn dj_image_url(image: &str) -> String {
    format!("{DJ_IMAGE_BASE}{image}")
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Status {
    pub np: String,
    pub artist: String,
    pub title: String,
    pub listeners: i64,
    pub is_afk_stream: bool,
    pub requesting: bool,
    pub current: i64,
    pub start_time: i64,
    pub end_time: i64,
    pub track_id: i64,
    pub thread: Option<String>,
    pub dj: Dj,
    pub queue: Vec<ListEntry>,
    pub last_played: Vec<ListEntry>,
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Dj {
    pub id: i64,
    pub name: String,
    pub image: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ListEntry {
    pub meta: String,
    pub artist: String,
    pub title: String,
    pub timestamp: i64,
    pub is_request: bool,
}

impl Status {
    pub fn queue_visible(&self) -> bool {
        self.is_afk_stream
    }

    pub fn dj_image_url(&self) -> String {
        dj_image_url(&self.dj.image)
    }
}

#[derive(Debug)]
pub struct ParseError(pub String);

impl std::fmt::Display for ParseError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::error::Error for ParseError {}

#[derive(Deserialize)]
struct Envelope {
    main: RawMain,
}

#[derive(Deserialize)]
struct RawMain {
    np: String,
    listeners: i64,
    isafkstream: bool,
    current: i64,
    start_time: i64,
    end_time: i64,
    trackid: i64,
    thread: String,
    requesting: bool,
    dj: RawDj,
    queue: Vec<RawEntry>,
    lp: Vec<RawEntry>,
    tags: Vec<String>,
}

#[derive(Deserialize)]
struct RawDj {
    id: i64,
    djname: String,
    djimage: String,
}

#[derive(Deserialize)]
struct RawEntry {
    meta: String,
    timestamp: i64,
    #[serde(rename = "type")]
    kind: i64,
}

pub fn parse_status(json: &str) -> Result<Status, ParseError> {
    let env: Envelope = serde_json::from_str(json).map_err(|e| ParseError(e.to_string()))?;
    Ok(env.main.into())
}

impl From<RawMain> for Status {
    fn from(raw: RawMain) -> Self {
        let (artist, title) = split_np(&raw.np);
        let thread = match raw.thread.trim() {
            "" | "none" => None,
            t => Some(t.to_string()),
        };
        Status {
            np: raw.np,
            artist,
            title,
            listeners: raw.listeners,
            is_afk_stream: raw.isafkstream,
            requesting: raw.requesting,
            current: raw.current,
            start_time: raw.start_time,
            end_time: raw.end_time,
            track_id: raw.trackid,
            thread,
            dj: Dj {
                id: raw.dj.id,
                name: raw.dj.djname,
                image: raw.dj.djimage,
            },
            queue: raw.queue.into_iter().map(ListEntry::from).collect(),
            last_played: raw.lp.into_iter().map(ListEntry::from).collect(),
            tags: raw.tags,
        }
    }
}

impl From<RawEntry> for ListEntry {
    fn from(raw: RawEntry) -> Self {
        let (artist, title) = split_np(&raw.meta);
        ListEntry {
            meta: raw.meta,
            artist,
            title,
            timestamp: raw.timestamp,
            is_request: raw.kind == 1,
        }
    }
}
