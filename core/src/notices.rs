//! DJ-online and fave-on-air notice policy. See `docs/spec/alarm-sleep-dj.md`.

use crate::parse::Status;

/// Hanyuu / AFK streamer, including a lagged `isafkstream`.
pub fn is_hanyuu(is_afk: bool, name: &str) -> bool {
    if is_afk {
        return true;
    }
    let n = name.trim();
    n.eq_ignore_ascii_case("hanyuu") || n.eq_ignore_ascii_case("hanyuu-sama")
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct DjNotice {
    pub live: bool,
    pub body: String,
}

/// `None` on first sample after opt-in, stream-down, AFK→Hanyuu, or np-only change.
#[uniffi::export]
pub fn dj_notice(
    prev: Option<Status>,
    next: Status,
    stream_down: bool,
    first_after_opt_in: bool,
) -> Option<DjNotice> {
    if first_after_opt_in || stream_down {
        return None;
    }
    let prev = prev?;
    let prev_h = is_hanyuu(prev.is_afk, &prev.dj.name);
    let next_h = is_hanyuu(next.is_afk, &next.dj.name);
    if prev_h && !next_h {
        let name = next.dj.name.trim();
        let body = if name.is_empty() {
            "A DJ is LIVE".into()
        } else {
            format!("{name} is LIVE")
        };
        return Some(DjNotice { live: true, body });
    }
    if !prev_h && next_h {
        let name = next.dj.name.trim();
        let body = if name.is_empty() {
            "Hanyuu-sama is back".into()
        } else {
            format!("{name} is back")
        };
        return Some(DjNotice { live: false, body });
    }
    if !prev_h && !next_h && (prev.dj.id != next.dj.id || prev.dj.name != next.dj.name) {
        let name = next.dj.name.trim();
        let body = if name.is_empty() {
            "A DJ is LIVE".into()
        } else {
            format!("{name} is LIVE")
        };
        return Some(DjNotice { live: true, body });
    }
    None
}

/// On-air identity for fave-currently-playing. Live DJ never uses leftover `trackid`.
#[uniffi::export]
pub fn fave_on_air_key(is_afk: bool, track_id: i64, np: String) -> String {
    if is_afk && track_id > 0 {
        format!("id:{track_id}")
    } else {
        format!("np:{}", np.trim())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parse::parse_status_str;

    fn afk() -> Status {
        parse_status_str(include_str!("../tests/fixtures/api_snapshot.json")).unwrap()
    }

    fn live() -> Status {
        parse_status_str(include_str!("../tests/fixtures/api_live_dj.json")).unwrap()
    }

    #[test]
    fn hanyuu_names() {
        assert!(is_hanyuu(true, "Anyone"));
        assert!(is_hanyuu(false, "Hanyuu-sama"));
        assert!(!is_hanyuu(false, "Claud"));
    }

    #[test]
    fn afk_to_live_notifies() {
        let n = dj_notice(Some(afk()), live(), false, false).unwrap();
        assert!(n.live);
        assert!(n.body.contains("LIVE"));
    }

    #[test]
    fn live_to_afk_notifies_back() {
        let n = dj_notice(Some(live()), afk(), false, false).unwrap();
        assert!(!n.live);
        assert!(n.body.contains("back"));
    }

    #[test]
    fn first_sample_silent() {
        assert!(dj_notice(Some(afk()), live(), false, true).is_none());
        assert!(dj_notice(None, live(), false, false).is_none());
    }

    #[test]
    fn stream_down_silent() {
        assert!(dj_notice(Some(afk()), live(), true, false).is_none());
    }

    #[test]
    fn np_change_is_not_a_dj_notice() {
        let mut a = afk();
        let mut b = afk();
        a.np = "A - 1".into();
        b.np = "B - 2".into();
        assert!(dj_notice(Some(a), b, false, false).is_none());
    }

    #[test]
    fn live_dj_key_is_np() {
        assert_eq!(fave_on_air_key(false, 99, "A - B".into()), "np:A - B");
        assert_eq!(fave_on_air_key(true, 12, "A - B".into()), "id:12");
    }
}
