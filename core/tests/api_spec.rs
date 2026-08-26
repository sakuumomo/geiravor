use std::time::Duration;

use geiravor_core::{
    dj_image_url, parse_status, poll_interval, relative_last_played, relative_queue, song_progress,
    split_np, NowPlayingEvent, NowPlayingState, API_URL, STREAM_URL, USER_AGENT,
};

fn snapshot_json() -> &'static str {
    include_str!("fixtures/api_snapshot.json")
}

fn live_dj_json() -> &'static str {
    include_str!("fixtures/api_live_dj.json")
}

#[test]
fn split_np_uses_first_separator() {
    assert_eq!(
        split_np("Aimer with chelly (EGOIST) - ninelie"),
        ("Aimer with chelly (EGOIST)".into(), "ninelie".into())
    );
    assert_eq!(
        split_np("3L - ・－・・ －－－ ・・・－ ・"),
        ("3L".into(), "・－・・ －－－ ・・・－ ・".into())
    );
}

#[test]
fn split_np_without_separator_is_title_only() {
    assert_eq!(split_np("just a title"), ("".into(), "just a title".into()));
}

#[test]
fn parse_live_snapshot_ignores_html_time_and_username() {
    let s = parse_status(snapshot_json()).expect("parse");
    assert_eq!(s.artist, "Hirasawa Susumu");
    assert_eq!(s.title, "Gats");
    assert_eq!(s.dj.name, "Hanyuu-sama");
    assert_ne!(s.dj.name, "AFK");
    assert!(s.is_afk_stream);
    assert_eq!(s.thread, None);
    assert_eq!(s.queue.len(), 5);
    assert!(s.queue[0].is_request);
    assert_eq!(s.queue[0].timestamp, 1787672537);
    assert_eq!(s.last_played.len(), 5);
    assert!(!s.last_played[0].is_request);
    assert_eq!(s.tags, ["berserk", "gattsu", "guts"]);
    assert!(s.queue_visible());
    assert_eq!(
        s.dj_image_url(),
        "https://r-a-d.io/api/dj-image/18-e0177611a37081b5.png"
    );
}

#[test]
fn parse_live_dj_hides_queue_and_unknown_duration() {
    let s = parse_status(live_dj_json()).expect("parse");
    assert!(!s.is_afk_stream);
    assert!(!s.queue_visible());
    assert!(!s.queue.is_empty(), "API may still send a queue");
    assert_eq!(s.thread.as_deref(), Some("https://boards.4chan.org/a/thread/1"));
    let p = song_progress(&s, 1000, 1000);
    assert_eq!(p.elapsed_secs, 0);
    assert_eq!(p.duration_secs, None);
}

#[test]
fn live_dj_ignores_catalog_track_window() {
    let json = live_dj_json().replace("\"end_time\": 0", "\"end_time\": 1500");
    let s = parse_status(&json).expect("parse");
    assert!(!s.is_afk_stream);
    assert!(s.end_time > s.start_time, "API may still send a catalog length");
    let p = song_progress(&s, 1000, 1100);
    assert_eq!(p.duration_secs, None);
    assert_eq!(p.elapsed_secs, 0);
}

#[test]
fn song_progress_uses_server_clock_offset_and_clamps() {
    let s = parse_status(snapshot_json()).expect("parse");
    let duration = s.end_time - s.start_time;
    let local_fetch = 0;
    let local_now = 0;
    let p = song_progress(&s, local_fetch, local_now);
    let expected = s.current - s.start_time;
    assert_eq!(p.elapsed_secs, expected);
    assert_eq!(p.duration_secs, Some(duration));

    let p_future = song_progress(&s, local_fetch, duration + 10_000);
    assert_eq!(p_future.elapsed_secs, duration);
}

#[test]
fn relative_times_match_site_style() {
    assert_eq!(relative_last_played(100, 130), "<1 minute ago");
    assert_eq!(relative_last_played(100, 160), "1 minute ago");
    assert_eq!(relative_last_played(100, 100 + 7 * 60), "7 minutes ago");
    assert_eq!(relative_queue(100 + 30, 100), "in <1 minute");
    assert_eq!(relative_queue(100 + 3 * 60, 100), "in 3 minutes");
}

#[test]
fn poll_interval_floor_two_seconds_and_backoff() {
    assert_eq!(poll_interval(true, false, 0), Duration::from_secs(2));
    assert_eq!(poll_interval(false, true, 0), Duration::from_secs(2));
    assert_eq!(poll_interval(false, false, 0), Duration::from_secs(15));
    assert_eq!(poll_interval(true, true, 1), Duration::from_secs(4));
    assert_eq!(poll_interval(false, false, 8), Duration::from_secs(60));
}

#[test]
fn icy_change_requests_refetch_without_replacing_np() {
    let s = parse_status(snapshot_json()).expect("parse");
    let original = s.np.clone();
    let mut state = NowPlayingState::default();
    state.apply(NowPlayingEvent::Snapshot(s), 0);
    state.apply(NowPlayingEvent::IcyTitle("Someone Else - Other".into()), 1);
    let status = state.status.as_ref().expect("status");
    assert_eq!(status.np, original);
    assert!(state.refetch);
    state.refetch = false;
    state.apply(NowPlayingEvent::IcyTitle("Someone Else - Other".into()), 2);
    assert!(
        !state.refetch,
        "same ICY title should not keep asking for a fetch"
    );
}

#[test]
fn stream_error_marks_stream_down_without_clearing_status() {
    let s = parse_status(snapshot_json()).expect("parse");
    let mut state = NowPlayingState::default();
    state.apply(NowPlayingEvent::Snapshot(s), 0);
    state.apply(NowPlayingEvent::StreamError, 1);
    assert!(state.stream_down);
    assert!(state.status.is_some());
}

#[test]
fn user_agent_and_urls_match_semver_and_spec() {
    assert_eq!(USER_AGENT, "Geiravor/0.1.0");
    assert_eq!(API_URL, "https://r-a-d.io/api");
    assert_eq!(STREAM_URL, "https://stream.r-a-d.io/main.mp3");
    assert!(!STREAM_URL.starts_with("https://r-a-d.io/main"));
    assert_eq!(
        dj_image_url("18-e0177611a37081b5.png"),
        "https://r-a-d.io/api/dj-image/18-e0177611a37081b5.png"
    );
}
