//! Snapshot parse. Fixtures from live `/api` envelope (lowercase `main`).

use geiravor_core::*;

fn fixture(name: &str) -> String {
    let path = format!("{}/tests/fixtures/{name}", env!("CARGO_MANIFEST_DIR"));
    std::fs::read_to_string(path).unwrap()
}

#[test]
fn afk_snapshot() {
    let s = parse_status_str(&fixture("api_snapshot.json")).unwrap();
    assert!(s.is_afk);
    assert_eq!(s.artist, "Hirasawa Susumu");
    assert_eq!(s.title, "Gats");
    assert_eq!(s.tags, vec!["berserk", "gattsu", "guts"]);
    assert_eq!(s.queue.len(), 5);
    assert!(s.queue[0].is_request);
    assert_eq!(s.dj.name, "Hanyuu-sama");
    assert!(!thread_visible(s.is_afk, &s.thread));
    let p = song_progress(&s, s.current, s.current);
    assert!(p.known);
    assert_eq!(p.duration_secs, s.end_time - s.start_time);
}

#[test]
fn live_dj_tags_null_and_no_clock() {
    let s = parse_status_str(&fixture("api_live_dj.json")).unwrap();
    assert!(!s.is_afk);
    assert!(s.tags.is_empty());
    assert_eq!(s.dj.name, "Ojiisan");
    assert!(thread_visible(s.is_afk, &s.thread));
    let p = song_progress(&s, 1000, 1000);
    assert!(!p.known);
}

#[test]
fn extra_keys_do_not_fail() {
    let mut v: serde_json::Value = serde_json::from_str(&fixture("api_snapshot.json")).unwrap();
    v["main"]["brand_new"] = serde_json::json!("ok");
    parse_status_str(&v.to_string()).unwrap();
}

#[test]
fn relative_labels() {
    assert_eq!(relative_last_played(100, 90), "<1 minute ago");
    assert_eq!(relative_last_played(160, 100), "1 minute ago");
    assert_eq!(relative_queue(100, 220), "in 2 minutes");
}
