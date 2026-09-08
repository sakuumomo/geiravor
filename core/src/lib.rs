//! Domain crate for Geiravor.
//!
//! Listener-facing behavior is specified in [`docs/spec/`](../docs/spec/).
//! This crate must not depend on Android types.

uniffi::setup_scaffolding!();

mod error;
mod faves;
mod html;
mod irc;
mod log_init;
mod net;
mod news;
mod notices;
mod parse;
mod poll;
mod radio;
mod reducer;
mod schedule;
mod search;
mod staff;
mod store;
mod theme;

/// Cargo package version (`X.Y.Z`). Identical to `versionName` and the User-Agent suffix.
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// HTTP User-Agent: `Geiravor/X.Y.Z`.
pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));

pub use error::ApiError;
pub use faves::{
    FaveRow, fave_requestable, faves_html_last_page, faves_html_url, faves_json_url,
    parse_faves_json, trim_faves_overlap,
};
pub use html::{extract_csrf, path_encode};
pub use irc::{
    DEFAULT_BOUNCER_PORT, FaveConfig, FaveKind, FaveResult, IrcError, IrcProfile, RIZON_HOST,
    RIZON_PORT, TapSnapshot, TlsIrc, attach_nick, certificate_fingerprint_sha256, connect_irc,
    irc_nick, nick_is_empty, run_add_fave, run_probe, tcp_connect_timeout,
};
pub use log_init::init_logging;
pub use net::{Coalescer, HttpClient, ReqwestClient};
pub use news::{
    NewsArticle, NewsCard, NewsComment, NewsList, RoleColor, news_article_url, news_list_url,
    parse_news_article, parse_news_list,
};
pub use notices::{DjNotice, dj_notice, fave_on_air_key, is_hanyuu};
pub use parse::{
    API_URL, DJ_IMAGE_BASE, Dj, HOME_URL, ListEntry, MAX_BODY, STREAM_URL, SongProgress, Status,
    check_bound, dj_image_url, format_clock, last_paint_chrome, parse_status, parse_status_str,
    parse_theme_name, relative_last_played, relative_queue, song_progress, song_progress_at,
    split_np, thread_is_visible, thread_visible,
};
pub use poll::poll_interval;
pub use radio::{RadioCore, StatusListener};
pub use reducer::{NowPlayingEvent, NowPlayingState, reduce};
pub use schedule::{EST_ZONE, SCHEDULE_URL, ScheduleDay, WEEKDAYS, est_zone_id, parse_schedule};
pub use search::{
    CAN_REQUEST_URL, RequestResult, SEARCH_HTML_URL, SearchPage, SearchTrack, parse_can_request,
    parse_request_body, parse_search, request_url, search_url,
};
pub use staff::{STAFF_URL, StaffCard, StaffGroup, parse_staff};
pub use store::{Store, payload_changed};
pub use theme::{
    ThemePack, decide_theme, holiday_window, theme_pack_from_pref, theme_pack_is_night,
    theme_pack_pref,
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_the_rewrite_placeholder() {
        assert_eq!(VERSION, "0.3.0");
    }

    #[test]
    fn user_agent_uses_package_version() {
        assert_eq!(USER_AGENT, concat!("Geiravor/", env!("CARGO_PKG_VERSION")));
    }
}
