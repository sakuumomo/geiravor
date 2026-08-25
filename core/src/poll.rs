use std::time::Duration;

pub fn poll_interval(ui_visible: bool, playing: bool, consecutive_failures: u32) -> Duration {
    if consecutive_failures > 0 {
        let shift = consecutive_failures.min(16);
        let secs = 2u64.saturating_mul(1u64 << shift).min(60);
        return Duration::from_secs(secs);
    }
    if ui_visible || playing {
        Duration::from_secs(2)
    } else {
        Duration::from_secs(15)
    }
}
