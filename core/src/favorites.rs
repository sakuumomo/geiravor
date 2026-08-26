use serde::Deserialize;

use crate::http::ApiError;
use crate::np::split_np;
use crate::search::encode_path_segment;

pub const FAVES_URL: &str = "https://r-a-d.io/faves";

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

    #[test]
    fn faves_url_encodes_kethsar() {
        assert_eq!(
            faves_url("Kethsar", 1),
            "https://r-a-d.io/faves?nick=Kethsar&page=1&dl=true"
        );
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
}
