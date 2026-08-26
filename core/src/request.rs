/// Station song-request cooldown (`radio.CalculateRequestDelay` / `Song.Requestable`).
#[uniffi::export]
pub fn request_delay_secs(request_count: i64) -> i64 {
    let count = request_count.clamp(0, 30) as f64;
    let dur = if count <= 7.0 {
        -11_057.0 * count * count + 172_954.0 * count + 81_720.0
    } else {
        599_955.0 * (0.0372 * count).exp() + 0.5
    };
    (dur / 2.0).trunc() as i64
}

/// Faves JSON has no `requestable`; gray Request from lastplayed / lastrequested / requestcount.
#[uniffi::export]
pub fn song_requestable(
    last_played: Option<i64>,
    last_requested: Option<i64>,
    request_count: Option<i64>,
    now: i64,
) -> bool {
    let delay = request_delay_secs(request_count.unwrap_or(0));
    if delay <= 0 {
        return false;
    }
    aged_enough(last_played, now, delay) && aged_enough(last_requested, now, delay)
}

fn aged_enough(last: Option<i64>, now: i64, delay: i64) -> bool {
    match last {
        None | Some(0) => true,
        Some(t) => now.saturating_sub(t) >= delay,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn delay_matches_valkyrie_for_low_and_high_counts() {
        assert_eq!(request_delay_secs(0), 40_860);
        assert_eq!(request_delay_secs(7), 375_302);
        assert_eq!(request_delay_secs(30), request_delay_secs(99));
        assert!(request_delay_secs(8) > request_delay_secs(7));
    }

    #[test]
    fn never_played_or_requested_is_requestable() {
        assert!(song_requestable(None, None, Some(0), 1_700_000_000));
        assert!(song_requestable(Some(0), Some(0), Some(0), 1_700_000_000));
    }

    #[test]
    fn recent_play_or_request_is_on_cooldown() {
        let now = 1_700_000_000;
        let delay = request_delay_secs(0);
        assert!(!song_requestable(Some(now - 10), None, Some(0), now));
        assert!(!song_requestable(None, Some(now - 10), Some(0), now));
        assert!(song_requestable(Some(now - delay), None, Some(0), now));
        assert!(song_requestable(None, Some(now - delay), Some(0), now));
    }
}
