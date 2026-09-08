//! Poll interval. Product: [`docs/spec/api.md`](../../docs/spec/api.md).

/// Seconds until the next `/api` GET.
pub fn poll_interval(ui_visible: bool, playing: bool, consecutive_failures: u32) -> u64 {
    if consecutive_failures > 0 {
        let exp = 2u64.saturating_pow(consecutive_failures.min(6));
        return (2 * exp).min(60);
    }
    if ui_visible || playing { 2 } else { 15 }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn floor_two_when_visible_or_playing() {
        assert_eq!(poll_interval(true, false, 0), 2);
        assert_eq!(poll_interval(false, true, 0), 2);
    }

    #[test]
    fn background_stopped_is_fifteen() {
        assert_eq!(poll_interval(false, false, 0), 15);
    }

    #[test]
    fn backoff_caps_at_sixty() {
        assert_eq!(poll_interval(true, true, 1), 4);
        assert!(poll_interval(false, false, 20) <= 60);
        assert_eq!(poll_interval(false, false, 20), 60);
    }
}
