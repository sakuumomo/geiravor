//! Now-playing state machine. Stream-down is a player event, not an `/api` miss.

use crate::parse::Status;

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct NowPlayingState {
    pub status: Option<Status>,
    pub stream_down: bool,
    pub playing: bool,
}

#[derive(Debug, Clone)]
pub enum NowPlayingEvent {
    Snapshot(Box<Status>),
    Playing(bool),
    PlayerError,
    PlayerPlaying,
}

/// Apply one event. A successful snapshot does not clear stream-down.
pub fn reduce_in_place(state: &mut NowPlayingState, event: NowPlayingEvent) {
    match event {
        NowPlayingEvent::Snapshot(status) => state.status = Some(*status),
        NowPlayingEvent::Playing(playing) => state.playing = playing,
        NowPlayingEvent::PlayerError => state.stream_down = true,
        NowPlayingEvent::PlayerPlaying => {
            state.stream_down = false;
            state.playing = true;
        }
    }
}

/// Apply one event. A successful snapshot does not clear stream-down.
pub fn reduce(mut state: NowPlayingState, event: NowPlayingEvent) -> NowPlayingState {
    reduce_in_place(&mut state, event);
    state
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parse::parse_status_str;

    #[test]
    fn snapshot_does_not_clear_stream_down() {
        let json = include_str!("../tests/fixtures/api_snapshot.json");
        let status = parse_status_str(json).unwrap();
        let down = reduce(NowPlayingState::default(), NowPlayingEvent::PlayerError);
        assert!(down.stream_down);
        let next = reduce(down, NowPlayingEvent::Snapshot(Box::new(status)));
        assert!(next.stream_down);
        assert!(next.status.is_some());
        let up = reduce(next, NowPlayingEvent::PlayerPlaying);
        assert!(!up.stream_down);
        assert!(up.playing);
    }
}
