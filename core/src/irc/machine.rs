use super::io::IrcIo;
use super::protocol::{HanyuuReply, np_match};
use super::session::wait_hanyuu;
use super::{FaveResult, HANYUU, IrcError};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TapSnapshot {
    pub is_afk: bool,
    pub track_id: i64,
    pub np: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FaveCommand {
    Fave,
    FaveLast,
    FaveId(i64),
    Unfave,
    UnfaveLast,
    UnfaveId(i64),
}

impl FaveCommand {
    pub fn payload(self) -> String {
        match self {
            Self::Fave => ".fave".into(),
            Self::FaveLast => ".fave last".into(),
            Self::FaveId(id) => format!(".fave {id}"),
            Self::Unfave => ".unfave".into(),
            Self::UnfaveLast => ".unfave last".into(),
            Self::UnfaveId(id) => format!(".unfave {id}"),
        }
    }
}

pub fn privmsg(cmd: FaveCommand) -> String {
    format!("PRIVMSG {HANYUU} :{}", cmd.payload())
}

pub fn run_fave_machine<I: IrcIo>(
    io: &mut I,
    tap: &TapSnapshot,
    latest_np: impl Fn() -> String,
    unfave: bool,
    catalog_id: Option<i64>,
) -> Result<FaveResult, IrcError> {
    if unfave {
        return unfave_by_id(io, catalog_id);
    }
    if tap.is_afk && tap.track_id > 0 {
        return afk_fave(io, tap.track_id);
    }
    live_dj_fave(io, &tap.np, latest_np)
}

fn afk_fave<I: IrcIo>(io: &mut I, track_id: i64) -> Result<FaveResult, IrcError> {
    io.send(&privmsg(FaveCommand::FaveId(track_id)))?;
    match wait_hanyuu(io)? {
        HanyuuReply::Added(name) | HanyuuReply::Already(name) => {
            Ok(FaveResult::success(name, true))
        }
        HanyuuReply::UnknownId => Ok(FaveResult::failed("I don't know of a song with that ID...")),
        other => Ok(FaveResult::failed(display_other(&other))),
    }
}

fn unfave_by_id<I: IrcIo>(io: &mut I, catalog_id: Option<i64>) -> Result<FaveResult, IrcError> {
    let Some(id) = catalog_id.filter(|id| *id > 0) else {
        return Ok(FaveResult::failed("Need a catalog ID to unfave."));
    };
    io.send(&privmsg(FaveCommand::UnfaveId(id)))?;
    match wait_hanyuu(io)? {
        HanyuuReply::Removed(name) | HanyuuReply::NotFavorited(name) => {
            Ok(FaveResult::success(name, false))
        }
        HanyuuReply::UnknownId => Ok(FaveResult::failed("I don't know of a song with that ID...")),
        other => Ok(FaveResult::failed(display_other(&other))),
    }
}

fn live_dj_fave<I: IrcIo>(
    io: &mut I,
    intended: &str,
    latest_np: impl Fn() -> String,
) -> Result<FaveResult, IrcError> {
    let started_with_last = !np_match(intended, &latest_np());
    let first = if started_with_last {
        FaveCommand::FaveLast
    } else {
        FaveCommand::Fave
    };
    io.send(&privmsg(first))?;
    let reply = wait_hanyuu(io)?;
    if reply.matches_intended(intended) {
        return Ok(FaveResult::success(reply.named().unwrap_or(intended), true));
    }
    if matches!(reply, HanyuuReply::UnknownId) {
        return Ok(FaveResult::failed("I don't know of a song with that ID..."));
    }
    if !reply.is_added() {
        return Ok(FaveResult::failed(display_other(&reply)));
    }
    if started_with_last {
        io.send(&privmsg(FaveCommand::UnfaveLast))?;
        let _ = wait_hanyuu(io);
        return Ok(FaveResult::failed("Could not fave the intended song."));
    }
    io.send(&privmsg(FaveCommand::Unfave))?;
    let _ = wait_hanyuu(io);
    io.send(&privmsg(FaveCommand::FaveLast))?;
    let second = wait_hanyuu(io)?;
    if second.matches_intended(intended) {
        return Ok(FaveResult::success(second.named().unwrap_or(intended), true));
    }
    if second.is_added() {
        io.send(&privmsg(FaveCommand::UnfaveLast))?;
        let _ = wait_hanyuu(io);
    }
    Ok(FaveResult::failed("Could not fave the intended song."))
}

fn display_other(reply: &HanyuuReply) -> String {
    match reply {
        HanyuuReply::Other(s) => s.clone(),
        HanyuuReply::Removed(n) => format!("'{n}' is removed from your favorites."),
        HanyuuReply::NotFavorited(n) => format!("You don't have '{n}' in your favorites."),
        HanyuuReply::Added(n) => n.clone(),
        HanyuuReply::Already(n) => n.clone(),
        HanyuuReply::UnknownId => "I don't know of a song with that ID...".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::irc::io::ScriptedIo;
    use crate::irc::{FaveKind, HANYUU};

    fn added(name: &str) -> String {
        format!(":{HANYUU}!bot@r-a-d.io NOTICE Geiravor :Added '{name}' to your favorites.")
    }

    fn already(name: &str) -> String {
        format!(":{HANYUU}!bot@r-a-d.io NOTICE Geiravor :You already have '{name}' favorited.")
    }

    #[test]
    fn afk_sends_track_id_and_treats_already_as_success() {
        let mut io = ScriptedIo::new(vec![already("Hirasawa Susumu - Gats")]);
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: "ignored".into(),
        };
        let result = run_fave_machine(&mut io, &tap, || "ignored".into(), false, None).unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert!(result.favorited);
        assert_eq!(result.message, "Hirasawa Susumu - Gats");
        assert_eq!(
            io.sent,
            vec![format!("PRIVMSG {HANYUU} :.fave 42")]
        );
        assert!(!io.sent.iter().any(|l| l.contains("last")));
    }

    #[test]
    fn live_dj_uses_fave_last_when_np_changed_and_unfaves_added_wrong() {
        let intended = "Wanted - Song";
        let mut io = ScriptedIo::new(vec![
            added("Wrong - Current"),
            format!(":{HANYUU}!bot@r-a-d.io NOTICE Geiravor :'Wrong - Current' is removed from your favorites."),
        ]);
        let tap = TapSnapshot {
            is_afk: false,
            track_id: 99,
            np: intended.into(),
        };
        let result = run_fave_machine(&mut io, &tap, || "Other - Now".into(), false, None).unwrap();
        assert_eq!(result.kind, FaveKind::Failed);
        assert_eq!(
            io.sent,
            vec![
                format!("PRIVMSG {HANYUU} :.fave last"),
                format!("PRIVMSG {HANYUU} :.unfave last"),
            ]
        );
        assert!(!io.sent.iter().any(|l| l.contains("99")));
        assert!(!io.sent.iter().any(|l| l.contains("last last")));
    }

    #[test]
    fn live_dj_unfave_only_if_added_then_retries_last() {
        let intended = "Wanted - Song";
        let mut io = ScriptedIo::new(vec![
            added("Wrong - Current"),
            format!(":{HANYUU}!bot@r-a-d.io NOTICE Geiravor :'Wrong - Current' is removed from your favorites."),
            added(intended),
        ]);
        let tap = TapSnapshot {
            is_afk: false,
            track_id: 0,
            np: intended.into(),
        };
        let result = run_fave_machine(&mut io, &tap, || intended.into(), false, None).unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert_eq!(result.message, intended);
        assert_eq!(
            io.sent,
            vec![
                format!("PRIVMSG {HANYUU} :.fave"),
                format!("PRIVMSG {HANYUU} :.unfave"),
                format!("PRIVMSG {HANYUU} :.fave last"),
            ]
        );
    }

    #[test]
    fn live_dj_does_not_unfave_already_favorited_wrong_title() {
        let mut io = ScriptedIo::new(vec![already("Wrong - Title")]);
        let tap = TapSnapshot {
            is_afk: false,
            track_id: 1,
            np: "Wanted - Song".into(),
        };
        let result = run_fave_machine(&mut io, &tap, || "Wanted - Song".into(), false, None).unwrap();
        assert_eq!(result.kind, FaveKind::Failed);
        assert_eq!(io.sent, vec![format!("PRIVMSG {HANYUU} :.fave")]);
    }

    #[test]
    fn afk_with_zero_track_id_uses_live_dj_machine() {
        let mut io = ScriptedIo::new(vec![added("Wanted - Song")]);
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 0,
            np: "Wanted - Song".into(),
        };
        let result = run_fave_machine(&mut io, &tap, || "Wanted - Song".into(), false, None).unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert_eq!(io.sent, vec![format!("PRIVMSG {HANYUU} :.fave")]);
    }

    #[test]
    fn catalog_unfave_sends_id_and_treats_removed_as_success() {
        let mut io = ScriptedIo::new(vec![
            format!(":{HANYUU}!bot@r-a-d.io NOTICE Geiravor :'Hirasawa Susumu - Gats' is removed from your favorites."),
        ]);
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: "Hirasawa Susumu - Gats".into(),
        };
        let result = run_fave_machine(&mut io, &tap, || tap.np.clone(), true, Some(42)).unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert!(!result.favorited);
        assert_eq!(result.message, "Hirasawa Susumu - Gats");
        assert_eq!(io.sent, vec![format!("PRIVMSG {HANYUU} :.unfave 42")]);
    }

    #[test]
    fn catalog_unfave_without_id_fails_without_send() {
        let mut io = ScriptedIo::new(vec![]);
        let tap = TapSnapshot {
            is_afk: false,
            track_id: 99,
            np: "Live - Only".into(),
        };
        let result = run_fave_machine(&mut io, &tap, || tap.np.clone(), true, None).unwrap();
        assert_eq!(result.kind, FaveKind::Failed);
        assert!(!result.favorited);
        assert!(io.sent.is_empty());
    }

    #[test]
    fn catalog_unfave_treats_not_favorited_as_success() {
        let mut io = ScriptedIo::new(vec![
            format!(":{HANYUU}!bot@r-a-d.io NOTICE Geiravor :You don't have 'Hirasawa Susumu - Gats' in your favorites."),
        ]);
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: "Hirasawa Susumu - Gats".into(),
        };
        let result = run_fave_machine(&mut io, &tap, || tap.np.clone(), true, Some(42)).unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert!(!result.favorited);
        assert_eq!(io.sent, vec![format!("PRIVMSG {HANYUU} :.unfave 42")]);
    }
}
