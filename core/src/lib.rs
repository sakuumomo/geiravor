uniffi::setup_scaffolding!();

mod csrf;
mod favorites;
mod http;
mod np;
mod poll;
mod progress;
mod radio;
mod reducer;
mod search;
mod status;

pub use csrf::{CSRF_BOOTSTRAP_URL, CSRF_COOKIE, CSRF_HEADER, extract_csrf_token, post_with_csrf};
pub use favorites::{FAVES_URL, FavoriteRow, faves_url, parse_faves};
pub use http::ApiError;
pub use np::split_np;
pub use poll::poll_interval;
pub use progress::{SongProgress, relative_last_played, relative_queue, song_progress};
pub use radio::{ApiClient, RadioCore, StatusListener};
pub use reducer::{NowPlayingEvent, NowPlayingState};
pub use search::{
    CAN_REQUEST_URL, RequestResult, SEARCH_URL, SearchHit, SearchPage, parse_can_request,
    parse_request_result, parse_search, search_url,
};
pub use status::{
    API_URL, Dj, ListEntry, ParseError, STREAM_URL, Status, dj_image_url, parse_status,
};

pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));
