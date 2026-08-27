uniffi::setup_scaffolding!();

mod csrf;
mod favorites;
mod http;
mod irc;
mod np;
mod poll;
mod progress;
mod radio;
mod reducer;
mod request;
mod search;
mod status;

pub use csrf::{CSRF_BOOTSTRAP_URL, CSRF_COOKIE, CSRF_HEADER, extract_csrf_token, post_with_csrf};
pub use favorites::{
    FAVES_PER_PAGE, FAVES_URL, FavoriteRow, FavoritesPage, faves_html_url, faves_url, parse_faves,
    parse_faves_last_page,
};
pub use http::ApiError;
pub use irc::{
    DEFAULT_BOUNCER_PORT, FaveConfig, FaveKind, FaveResult, IrcProfile, RIZON_HOST, RIZON_PORT,
    TapSnapshot, TlsIrc, connect_irc, nick_is_empty, run_add_fave,
};

#[uniffi::export]
pub fn certificate_fingerprint_sha256(pem: String) -> String {
    crate::irc::certificate_fingerprint_sha256(&pem)
}

pub use np::split_np;
pub use poll::poll_interval;
pub use progress::{SongProgress, relative_last_played, relative_queue, song_progress};
pub use radio::{ApiClient, RadioCore, StatusListener};
pub use reducer::{NowPlayingEvent, NowPlayingState};
pub use request::{request_delay_secs, song_requestable};
pub use search::{
    CAN_REQUEST_URL, RequestResult, SEARCH_URL, SearchHit, SearchPage, parse_can_request,
    parse_request_result, parse_search, search_url,
};
pub use status::{
    API_URL, Dj, ListEntry, ParseError, STREAM_URL, Status, dj_image_url, parse_status,
};

pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));
