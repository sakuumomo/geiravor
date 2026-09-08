//! Theme **decision**. Tokens/paint stay in Compose. See `docs/spec/ui.md`.

/// Committed packs. Other public names (`suzu`, `tuicss`, `edenlight`) are ignored.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ThemePack {
    DefaultDark,
    DefaultLight,
    Christmas,
    Halloween,
    NewYears,
}

impl ThemePack {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::DefaultDark => "default-dark",
            Self::DefaultLight => "default-light",
            Self::Christmas => "christmas",
            Self::Halloween => "halloween",
            Self::NewYears => "newyears",
        }
    }

    pub fn parse(name: &str) -> Self {
        match name.trim() {
            "default-light" => Self::DefaultLight,
            "christmas" => Self::Christmas,
            "halloween" => Self::Halloween,
            "newyears" => Self::NewYears,
            _ => Self::DefaultDark,
        }
    }

    /// Auto night: Default light is day; everything else is night.
    pub fn night(self) -> bool {
        !matches!(self, Self::DefaultLight)
    }
}

/// Device-local holiday window. Inclusive. None → do not sniff.
#[uniffi::export]
pub fn holiday_window(month: u8, day: u8) -> Option<ThemePack> {
    match (month, day) {
        (10, 29..=31) | (11, 1) => Some(ThemePack::Halloween),
        (12, 1..=26) => Some(ThemePack::Christmas),
        (12, 27..=31) | (1, 1..=3) => Some(ThemePack::NewYears),
        _ => None,
    }
}

/// User pick, unless a holiday window is open, opt-out is off, and the live
/// site is serving that holiday.
#[uniffi::export]
pub fn decide_theme(
    user_pick: ThemePack,
    opt_out: bool,
    month: u8,
    day: u8,
    sniffed: Option<String>,
) -> ThemePack {
    if opt_out {
        return user_pick;
    }
    let Some(window) = holiday_window(month, day) else {
        return user_pick;
    };
    match sniffed.as_deref().map(str::trim) {
        Some("christmas") if window == ThemePack::Christmas => ThemePack::Christmas,
        Some("halloween") if window == ThemePack::Halloween => ThemePack::Halloween,
        Some("newyears") if window == ThemePack::NewYears => ThemePack::NewYears,
        _ => user_pick,
    }
}

#[uniffi::export]
pub fn theme_pack_from_pref(name: String) -> ThemePack {
    ThemePack::parse(&name)
}

#[uniffi::export]
pub fn theme_pack_pref(pack: ThemePack) -> String {
    pack.as_str().to_string()
}

#[uniffi::export]
pub fn theme_pack_is_night(pack: ThemePack) -> bool {
    pack.night()
}

/// Hourly GET `/` when the window is open but the site is not on that holiday yet.
pub const SNIFF_INTERVAL_SECS: i64 = 3600;

/// Whether a dedicated `GET /` is allowed. Try cached news/schedule/staff HTML first.
#[uniffi::export]
pub fn should_sniff_home(
    opt_out: bool,
    month: u8,
    day: u8,
    now_epoch: i64,
    last_sniff_epoch: i64,
    seen_window_holiday: bool,
    process_start: bool,
) -> bool {
    if opt_out || holiday_window(month, day).is_none() {
        return false;
    }
    if process_start {
        return true;
    }
    if seen_window_holiday {
        return false;
    }
    last_sniff_epoch == 0 || now_epoch.saturating_sub(last_sniff_epoch) >= SNIFF_INTERVAL_SECS
}

/// Sniffed `/assets/{name}/` is the holiday pack for this device-local window.
#[uniffi::export]
pub fn sniffed_matches_window(month: u8, day: u8, sniffed: String) -> bool {
    match holiday_window(month, day) {
        Some(ThemePack::Christmas) => sniffed.trim() == "christmas",
        Some(ThemePack::Halloween) => sniffed.trim() == "halloween",
        Some(ThemePack::NewYears) => sniffed.trim() == "newyears",
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn windows() {
        assert_eq!(holiday_window(10, 28), None);
        assert_eq!(holiday_window(10, 29), Some(ThemePack::Halloween));
        assert_eq!(holiday_window(11, 1), Some(ThemePack::Halloween));
        assert_eq!(holiday_window(11, 2), None);
        assert_eq!(holiday_window(12, 1), Some(ThemePack::Christmas));
        assert_eq!(holiday_window(12, 26), Some(ThemePack::Christmas));
        assert_eq!(holiday_window(12, 27), Some(ThemePack::NewYears));
        assert_eq!(holiday_window(1, 3), Some(ThemePack::NewYears));
        assert_eq!(holiday_window(1, 4), None);
    }

    #[test]
    fn opt_out_keeps_user_pick() {
        assert_eq!(
            decide_theme(
                ThemePack::DefaultDark,
                true,
                12,
                25,
                Some("christmas".into()),
            ),
            ThemePack::DefaultDark
        );
    }

    #[test]
    fn holiday_overrides_when_site_matches_window() {
        assert_eq!(
            decide_theme(
                ThemePack::DefaultDark,
                false,
                12,
                25,
                Some("christmas".into()),
            ),
            ThemePack::Christmas
        );
    }

    #[test]
    fn ignored_public_names() {
        assert_eq!(
            decide_theme(ThemePack::DefaultLight, false, 12, 25, Some("suzu".into()),),
            ThemePack::DefaultLight
        );
    }

    #[test]
    fn outside_window_never_auto() {
        assert_eq!(
            decide_theme(
                ThemePack::DefaultDark,
                false,
                6,
                1,
                Some("christmas".into()),
            ),
            ThemePack::DefaultDark
        );
    }

    #[test]
    fn manual_holiday_is_a_valid_pick() {
        assert_eq!(ThemePack::parse("halloween"), ThemePack::Halloween);
        assert!(ThemePack::Halloween.night());
        assert!(!ThemePack::DefaultLight.night());
    }

    #[test]
    fn sniff_home_rules() {
        assert!(!should_sniff_home(true, 12, 25, 100, 0, false, true));
        assert!(!should_sniff_home(false, 6, 1, 100, 0, false, true));
        assert!(should_sniff_home(false, 12, 25, 100, 0, false, true));
        assert!(should_sniff_home(false, 12, 25, 100, 0, true, true));
        assert!(!should_sniff_home(false, 12, 25, 100, 10, true, false));
        assert!(!should_sniff_home(false, 12, 25, 100, 90, false, false));
        assert!(should_sniff_home(false, 12, 25, 3700, 1, false, false));
        assert!(sniffed_matches_window(12, 25, "christmas".into()));
        assert!(!sniffed_matches_window(12, 25, "default-dark".into()));
    }
}
