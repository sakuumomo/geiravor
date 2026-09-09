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
    assert_eq!(s.track_id, 15358);
    assert!(thread_visible(s.is_afk, &s.thread));
    let p = song_progress(&s, 1000, 1000);
    assert!(!p.known);
    assert_eq!(
        fave_on_air_key(s.is_afk, s.track_id, s.np.clone()),
        "np:Some Live DJ - No End"
    );
    assert!(!s.queue.is_empty());
}

#[test]
fn live_dj_null_lists_do_not_keep_afk_paint() {
    let mut v: serde_json::Value = serde_json::from_str(&fixture("api_live_dj.json")).unwrap();
    v["main"]["tags"] = serde_json::Value::Null;
    v["main"]["queue"] = serde_json::Value::Null;
    v["main"]["lp"] = serde_json::Value::Null;
    v["main"]["thread"] = serde_json::Value::Null;
    let s = parse_status_str(&v.to_string())
        .expect("null lists must parse; else last Hanyuu paint sticks");
    assert!(!s.is_afk);
    assert!(s.tags.is_empty());
    assert!(s.queue.is_empty());
    assert!(s.lp.is_empty());
    assert!(!thread_visible(s.is_afk, &s.thread));
    assert!(!song_progress(&s, 1000, 1000).known);
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
