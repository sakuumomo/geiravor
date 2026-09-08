//! Domain crate for Geiravor.
//!
//! Listener-facing behavior is specified in [`docs/spec/`](../docs/spec/).
//! This crate must not depend on Android types.

uniffi::setup_scaffolding!();

mod error;
mod irc;
mod net;
mod parse;
mod poll;
mod radio;
mod reducer;
mod store;
mod theme;

/// Cargo package version (`X.Y.Z`). Identical to `versionName` and the User-Agent suffix.
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// HTTP User-Agent: `Geiravor/X.Y.Z`.
pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));

pub use error::ApiError;
pub use irc::{
    DEFAULT_BOUNCER_PORT, FaveConfig, FaveKind, FaveResult, IrcError, IrcProfile, RIZON_HOST,
    RIZON_PORT, TapSnapshot, TlsIrc, attach_nick, certificate_fingerprint_sha256, connect_irc,
    irc_nick, nick_is_empty, run_add_fave, run_probe, tcp_connect_timeout,
};
pub use net::{Coalescer, HttpClient, ReqwestClient};
pub use parse::{
    API_URL, DJ_IMAGE_BASE, Dj, HOME_URL, ListEntry, MAX_BODY, STREAM_URL, SongProgress, Status,
    check_bound, dj_image_url, format_clock, last_paint_chrome, parse_status, parse_status_str,
    parse_theme_name, relative_last_played, relative_queue, song_progress, song_progress_at,
    split_np, thread_is_visible, thread_visible,
};
pub use poll::poll_interval;
pub use radio::{RadioCore, StatusListener};
pub use reducer::{NowPlayingEvent, NowPlayingState, reduce};
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
