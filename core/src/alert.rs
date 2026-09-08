//! Alarm / snooze / sleep durations. See `docs/spec/alarm-sleep-dj.md`.

pub const ALERT_MIN_MINUTES: u32 = 1;
pub const ALERT_MAX_MINUTES: u32 = 12 * 60;

/// Hours + minutes, clamped to 1 minute … 12 hours.
#[uniffi::export]
pub fn alert_duration_minutes(hours: u32, minutes: u32) -> u32 {
    hours
        .saturating_mul(60)
        .saturating_add(minutes)
        .clamp(ALERT_MIN_MINUTES, ALERT_MAX_MINUTES)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clamps_empty_to_one_minute() {
        assert_eq!(alert_duration_minutes(0, 0), 1);
    }

    #[test]
    fn ten_minutes_and_one_hour() {
        assert_eq!(alert_duration_minutes(0, 10), 10);
        assert_eq!(alert_duration_minutes(1, 0), 60);
        assert_eq!(alert_duration_minutes(2, 30), 150);
    }

    #[test]
    fn caps_at_twelve_hours() {
        assert_eq!(alert_duration_minutes(13, 0), 12 * 60);
        assert_eq!(alert_duration_minutes(0, 900), 12 * 60);
    }
}
