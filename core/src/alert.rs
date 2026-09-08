//! Alarm / snooze / sleep durations. See `docs/spec/alarm-sleep-dj.md`.

pub const ALERT_MIN_MINUTES: u32 = 1;
pub const ALERT_MAX_MINUTES: u32 = 12 * 60;

/// 24h hour (0–23) → 1–12 for an AM/PM field.
#[uniffi::export]
pub fn alarm_hour_12(hour24: u32) -> u32 {
    let h = hour24 % 24;
    if h.is_multiple_of(12) { 12 } else { h % 12 }
}

/// Whether [hour24] is PM.
#[uniffi::export]
pub fn alarm_is_pm(hour24: u32) -> bool {
    hour24 % 24 >= 12
}

/// 1–12 plus AM/PM → 0–23.
#[uniffi::export]
pub fn alarm_hour_24(hour12: u32, pm: bool) -> u32 {
    let h = hour12.clamp(1, 12);
    match (h, pm) {
        (12, false) => 0,
        (12, true) => 12,
        (_, false) => h,
        (_, true) => h + 12,
    }
}

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

    #[test]
    fn twelve_hour_round_trip() {
        assert_eq!(alarm_hour_12(0), 12);
        assert!(!alarm_is_pm(0));
        assert_eq!(alarm_hour_24(12, false), 0);
        assert_eq!(alarm_hour_12(7), 7);
        assert!(!alarm_is_pm(7));
        assert_eq!(alarm_hour_24(7, false), 7);
        assert_eq!(alarm_hour_12(12), 12);
        assert!(alarm_is_pm(12));
        assert_eq!(alarm_hour_24(12, true), 12);
        assert_eq!(alarm_hour_12(13), 1);
        assert!(alarm_is_pm(13));
        assert_eq!(alarm_hour_24(1, true), 13);
        assert_eq!(alarm_hour_12(23), 11);
        assert_eq!(alarm_hour_24(11, true), 23);
    }
}
