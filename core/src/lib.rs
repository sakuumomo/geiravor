uniffi::setup_scaffolding!();

mod np;
mod poll;
mod progress;
mod reducer;
mod status;

pub use np::split_np;
pub use poll::poll_interval;
pub use progress::{relative_last_played, relative_queue, song_progress, SongProgress};
pub use reducer::{NowPlayingEvent, NowPlayingState};
pub use status::{dj_image_url, parse_status, Dj, ListEntry, ParseError, Status, API_URL, STREAM_URL};

pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));

/// UniFFI smoke test.
#[uniffi::export]
pub fn add(left: u32, right: u32) -> u32 {
    left.saturating_add(right)
}

#[cfg(test)]
mod tests {
    use super::add;

    #[test]
    fn add_ones() {
        assert_eq!(add(1, 2), 3);
    }
}
