//! Domain crate for Geiravor.
//!
//! Listener-facing behavior is specified in [`docs/spec/`](../docs/spec/).
//! This crate must not depend on Android types.

uniffi::setup_scaffolding!();

mod error;
mod net;
mod parse;
mod poll;
mod radio;
mod reducer;
mod store;

/// Cargo package version (`X.Y.Z`). Identical to `versionName` and the User-Agent suffix.
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// HTTP User-Agent: `Geiravor/X.Y.Z`.
pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));

pub use error::ApiError;
pub use net::{Coalescer, HttpClient, ReqwestClient};
pub use parse::{
    API_URL, DJ_IMAGE_BASE, Dj, ListEntry, MAX_BODY, STREAM_URL, SongProgress, Status, check_bound,
    dj_image_url, last_paint_chrome, parse_status, parse_status_str, parse_theme_name,
    relative_last_played, relative_queue, song_progress, split_np, thread_visible,
};
pub use poll::poll_interval;
pub use radio::{RadioCore, StatusListener};
pub use reducer::{NowPlayingEvent, NowPlayingState, reduce};
pub use store::{Store, payload_changed};

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
