use crate::status::Status;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SongProgress {
    pub elapsed_secs: i64,
    pub duration_secs: Option<i64>,
}

pub fn song_progress(status: &Status, local_at_fetch: i64, local_now: i64) -> SongProgress {
    let elapsed = (local_now + (status.current - local_at_fetch)) - status.start_time;
    let duration = if status.end_time > status.start_time {
        Some(status.end_time - status.start_time)
    } else {
        None
    };
    let elapsed = elapsed.max(0);
    let elapsed = match duration {
        Some(d) => elapsed.min(d),
        None => elapsed,
    };
    SongProgress {
        elapsed_secs: elapsed,
        duration_secs: duration,
    }
}

fn minutes_phrase(delta_secs: i64, template_one: &str, template_many: &str) -> String {
    let secs = delta_secs.max(0);
    if secs < 60 {
        if template_one.starts_with("in ") {
            "in <1 minute".into()
        } else {
            "<1 minute ago".into()
        }
    } else {
        let n = secs / 60;
        if n == 1 {
            template_one.into()
        } else {
            template_many.replace("{n}", &n.to_string())
        }
    }
}

pub fn relative_last_played(entry_ts: i64, current: i64) -> String {
    minutes_phrase(current - entry_ts, "1 minute ago", "{n} minutes ago")
}

pub fn relative_queue(entry_ts: i64, current: i64) -> String {
    minutes_phrase(entry_ts - current, "in 1 minute", "in {n} minutes")
}
