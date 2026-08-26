uniffi::setup_scaffolding!();

mod http;
mod np;
mod poll;
mod progress;
mod radio;
mod reducer;
mod status;

pub use http::ApiError;
pub use np::split_np;
pub use poll::poll_interval;
pub use progress::{SongProgress, relative_last_played, relative_queue, song_progress};
pub use radio::{ApiClient, RadioCore, StatusListener};
pub use reducer::{NowPlayingEvent, NowPlayingState};
pub use status::{
    API_URL, Dj, ListEntry, ParseError, STREAM_URL, Status, dj_image_url, parse_status,
};

pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));
