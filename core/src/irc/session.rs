//! Handshake + Hanyuu fave machine. Never log secrets.

use std::time::Duration;

use super::io::IrcIo;
use super::protocol::{
    assigned_nick_001, cap_ls, hanyuu_added, hanyuu_already, hanyuu_not_favorited, hanyuu_removed,
    hanyuu_unknown, is_authenticate_plus, is_cap_ack, is_hanyuu_notice, is_nick_error,
    list_has_sasl, named_song, nicks_match, np_match, numeric,
};
use super::tls::IrcError;
use super::{FaveConfig, FaveResult, HANYUU, IrcProfile, TapSnapshot};

const FAVE_EXTRA_ATTEMPTS: u32 = 2;

/// Bouncer client id, not the public Rizon nick.
pub fn attach_nick() -> String {
    let n = std::process::id()
        ^ std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.subsec_nanos())
            .unwrap_or(1);
    format!("geiravor-{:04x}", n % 0xffff)
}

fn pong(line: &str) -> String {
    format!("PONG {}", line.get(5..).unwrap_or(""))
}

fn reply_ping<I: IrcIo>(io: &mut I, line: &str) -> Result<bool, IrcError> {
    if line.to_ascii_uppercase().starts_with("PING ") {
        io.send(&pong(line))?;
        return Ok(true);
    }
    Ok(false)
}

/// Handshake only (Settings → Test connection). No `.fave`.
pub fn run_probe<I: IrcIo>(
    conn: &mut I,
    cfg: &FaveConfig,
    expected_nick: &str,
) -> Result<(), IrcError> {
    handshake(conn, cfg, expected_nick)
}

/// After TLS is up: handshake, then AFK or live-DJ machine.
pub fn run_add_fave<I: IrcIo>(
    conn: &mut I,
    cfg: &FaveConfig,
    tap: &TapSnapshot,
    mut latest_np: impl FnMut() -> String,
    expected_nick: &str,
    unfave: bool,
    catalog_id: Option<i64>,
) -> Result<FaveResult, IrcError> {
    handshake(conn, cfg, expected_nick)?;
    let now = latest_np();
    if unfave {
        let id = catalog_id
            .filter(|i| *i > 0)
            .or(if tap.is_afk && tap.track_id > 0 {
                Some(tap.track_id)
            } else {
                None
            });
        return match id {
            Some(id) => afk_id(conn, tap, true, Some(id)),
            None => Ok(FaveResult::failed("no catalog id")),
        };
    }
    if tap.is_afk && tap.track_id > 0 {
        afk_id(conn, tap, false, catalog_id)
    } else {
        live_dj(conn, tap, &now)
    }
}

fn handshake<I: IrcIo>(
    conn: &mut I,
    cfg: &FaveConfig,
    expected_nick: &str,
) -> Result<(), IrcError> {
    let bouncer = matches!(cfg.profile, IrcProfile::Bouncer);
    if bouncer && !cfg.bouncer_pass.is_empty() {
        conn.send(&format!("PASS {}", cfg.bouncer_pass))?;
    }
    let want_sasl = !cfg.client_cert_pem.trim().is_empty() || !cfg.sasl_password.is_empty();
    if want_sasl {
        conn.send("CAP LS 302")?;
    }
    conn.send(&format!("NICK {expected_nick}"))?;
    conn.send("USER geiravor 0 * :Geiravor")?;

    let mut welcomed = false;
    let mut sasl_done = !want_sasl;
    let mut saw_sasl_cap = false;
    let mut cap_req_sent = false;
    let mut auth_mech_sent = false;
    let deadline = std::time::Instant::now() + Duration::from_secs(20);
    while !(welcomed && sasl_done) {
        if std::time::Instant::now() >= deadline {
            return Err(IrcError::Timeout);
        }
        let line = conn.recv()?;
        if reply_ping(conn, &line)? {
            continue;
        }
        if is_nick_error(&line) {
            return Err(IrcError::Protocol("nick rejected".into()));
        }
        if let Some((continued, list)) = cap_ls(&line) {
            if list_has_sasl(&list) {
                saw_sasl_cap = true;
            }
            if want_sasl && saw_sasl_cap && !cap_req_sent {
                cap_req_sent = true;
                conn.send("CAP REQ :sasl")?;
            } else if want_sasl && !continued && !saw_sasl_cap {
                return Err(IrcError::Protocol("SASL missing from CAP LS".into()));
            }
            continue;
        }
        if want_sasl && is_cap_ack(&line) && !auth_mech_sent {
            auth_mech_sent = true;
            if !cfg.client_cert_pem.trim().is_empty() {
                conn.send("AUTHENTICATE EXTERNAL")?;
            } else {
                conn.send("AUTHENTICATE PLAIN")?;
            }
            continue;
        }
        if want_sasl && is_authenticate_plus(&line) {
            if !cfg.client_cert_pem.trim().is_empty() {
                conn.send("AUTHENTICATE +")?;
            } else {
                let user = if cfg.sasl_username.trim().is_empty() {
                    super::irc_nick(cfg)
                } else {
                    cfg.sasl_username.trim()
                };
                let payload = format!("\0{user}\0{}", cfg.sasl_password);
                let b64 = base64::Engine::encode(
                    &base64::engine::general_purpose::STANDARD,
                    payload.as_bytes(),
                );
                conn.send(&format!("AUTHENTICATE {b64}"))?;
            }
            continue;
        }
        match numeric(&line) {
            Some("903") if want_sasl => {
                sasl_done = true;
                conn.send("CAP END")?;
            }
            Some("904" | "905" | "902" | "906" | "907") if want_sasl => {
                return Err(IrcError::Protocol("SASL failed".into()));
            }
            Some("001") => {
                if want_sasl && !saw_sasl_cap {
                    return Err(IrcError::Protocol("SASL missing from CAP LS".into()));
                }
                let assigned = assigned_nick_001(&line).unwrap_or_default();
                if !nicks_match(&assigned, expected_nick) {
                    return Err(IrcError::Protocol(format!(
                        "nick mismatch: got {assigned}, want {expected_nick}"
                    )));
                }
                welcomed = true;
            }
            _ => {}
        }
        if line.split_whitespace().nth(1) == Some("NICK") {
            return Err(IrcError::Protocol("server renamed nick".into()));
        }
    }

    if !want_sasl && !bouncer && !cfg.nickserv_password.is_empty() {
        conn.send(&format!(
            "PRIVMSG NickServ :IDENTIFY {}",
            cfg.nickserv_password
        ))?;
        wait_nickserv(conn)?;
    }
    Ok(())
}

fn wait_nickserv<I: IrcIo>(conn: &mut I) -> Result<(), IrcError> {
    conn.set_read_timeout(Duration::from_secs(5))?;
    let deadline = std::time::Instant::now() + Duration::from_secs(5);
    let result = loop {
        if std::time::Instant::now() >= deadline {
            break Ok(());
        }
        match conn.recv() {
            Ok(line) => {
                if reply_ping(conn, &line)? {
                    continue;
                }
                let lower = line.to_ascii_lowercase();
                if lower.contains("nickserv") {
                    if lower.contains("invalid") {
                        break Err(IrcError::Protocol("NickServ invalid".into()));
                    }
                    break Ok(());
                }
            }
            Err(IrcError::Timeout) => break Ok(()),
            Err(e) => break Err(e),
        }
    };
    conn.set_read_timeout(Duration::from_secs(30))?;
    result
}

fn wait_hanyuu<I: IrcIo>(conn: &mut I) -> Result<String, IrcError> {
    let deadline = std::time::Instant::now() + Duration::from_secs(15);
    while std::time::Instant::now() < deadline {
        let line = conn.recv()?;
        if reply_ping(conn, &line)? {
            continue;
        }
        if is_hanyuu_notice(&line) {
            return Ok(line);
        }
    }
    Err(IrcError::Timeout)
}

fn privmsg<I: IrcIo>(conn: &mut I, cmd: &str) -> Result<(), IrcError> {
    conn.send(&format!("PRIVMSG {HANYUU} :{cmd}"))
}

fn afk_id<I: IrcIo>(
    conn: &mut I,
    tap: &TapSnapshot,
    unfave: bool,
    catalog_id: Option<i64>,
) -> Result<FaveResult, IrcError> {
    let id = catalog_id.unwrap_or(tap.track_id);
    let cmd = if unfave {
        format!(".unfave {id}")
    } else {
        format!(".fave {id}")
    };
    privmsg(conn, &cmd)?;
    let notice = wait_hanyuu(conn)?;
    if hanyuu_unknown(&notice) {
        return Ok(FaveResult::failed("unknown id"));
    }
    if unfave {
        if hanyuu_removed(&notice) || hanyuu_not_favorited(&notice) {
            return Ok(FaveResult::success(tap.np.clone(), false));
        }
        return Ok(FaveResult::failed(named_song(&notice).unwrap_or_default()));
    }
    if hanyuu_added(&notice) || hanyuu_already(&notice) {
        return Ok(FaveResult::success(tap.np.clone(), true));
    }
    Ok(FaveResult::failed(named_song(&notice).unwrap_or_default()))
}

fn live_dj<I: IrcIo>(
    conn: &mut I,
    tap: &TapSnapshot,
    latest: &str,
) -> Result<FaveResult, IrcError> {
    let first = if np_match(latest, &tap.np) {
        ".fave"
    } else {
        ".fave last"
    };
    privmsg(conn, first)?;
    let notice = wait_hanyuu(conn)?;
    if hanyuu_unknown(&notice) {
        return Ok(FaveResult::failed("unknown"));
    }
    let named = named_song(&notice).unwrap_or_default();
    if np_match(&named, &tap.np) {
        return Ok(FaveResult::success(tap.np.clone(), true));
    }
    if hanyuu_already(&notice) {
        return Ok(FaveResult::failed(named));
    }
    if !hanyuu_added(&notice) {
        return Ok(FaveResult::failed(named));
    }
    if first == ".fave" {
        privmsg(conn, ".unfave")?;
        let _ = wait_hanyuu(conn)?;
        privmsg(conn, ".fave last")?;
        let n2 = wait_hanyuu(conn)?;
        let named2 = named_song(&n2).unwrap_or_default();
        if np_match(&named2, &tap.np) {
            return Ok(FaveResult::success(tap.np.clone(), true));
        }
        if hanyuu_added(&n2) {
            privmsg(conn, ".unfave last")?;
            let _ = wait_hanyuu(conn)?;
        }
        return Ok(FaveResult::failed(named2));
    }
    if first == ".fave last" && hanyuu_added(&notice) {
        privmsg(conn, ".unfave last")?;
        let _ = wait_hanyuu(conn)?;
    }
    Ok(FaveResult::failed(named))
}

/// Retry wrapper for Timeout / Network / TLS.
pub fn with_retries<T>(mut op: impl FnMut() -> Result<T, IrcError>) -> Result<T, IrcError> {
    let mut last = IrcError::Network("retry".into());
    for i in 0..=FAVE_EXTRA_ATTEMPTS {
        match op() {
            Ok(v) => return Ok(v),
            Err(e) if e.retryable() && i < FAVE_EXTRA_ATTEMPTS => {
                last = e;
            }
            Err(e) => return Err(e),
        }
    }
    Err(last)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::irc::io::ScriptedIo;
    use crate::irc::{FaveKind, IrcProfile};

    fn rizon_config(nick: &str) -> FaveConfig {
        FaveConfig {
            nick: nick.into(),
            list_nick: String::new(),
            profile: IrcProfile::Rizon,
            nickserv_password: String::new(),
            bouncer_host: String::new(),
            bouncer_port: 6697,
            bouncer_pass: String::new(),
            allow_insecure_tls: false,
            sasl_username: String::new(),
            sasl_password: String::new(),
            client_cert_pem: String::new(),
            client_key_pem: String::new(),
            tls_fingerprint: String::new(),
        }
    }

    fn bouncer_config(pass: &str) -> FaveConfig {
        FaveConfig {
            nick: "Geiravor".into(),
            list_nick: String::new(),
            profile: IrcProfile::Bouncer,
            nickserv_password: "should-not-send".into(),
            bouncer_host: "127.0.0.1".into(),
            bouncer_port: 6697,
            bouncer_pass: pass.into(),
            allow_insecure_tls: false,
            sasl_username: String::new(),
            sasl_password: String::new(),
            client_cert_pem: String::new(),
            client_key_pem: String::new(),
            tls_fingerprint: String::new(),
        }
    }

    fn sasl_cfg(user: &str, pass: &str, cert: &str) -> FaveConfig {
        let mut c = bouncer_config("");
        c.sasl_username = user.into();
        c.sasl_password = pass.into();
        c.client_cert_pem = cert.into();
        c
    }

    #[test]
    fn bouncer_no_quit_no_nickserv() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 7,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":irc 001 geiravor-aaaa :welcome".into(),
            format!(":{HANYUU}!b@r NOTICE x :Added 'A - B' to your favorites."),
        ]);
        let result = run_add_fave(
            &mut io,
            &bouncer_config("user/rizon:secret"),
            &tap,
            || "A - B".into(),
            "geiravor-aaaa",
            false,
            None,
        )
        .unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert_eq!(io.sent[0], "PASS user/rizon:secret");
        assert_eq!(io.sent[1], "NICK geiravor-aaaa");
        assert!(!io.sent.iter().any(|l| l.starts_with("QUIT")));
        assert!(!io.sent.iter().any(|l| l.contains("NickServ")));
        assert!(
            io.sent
                .iter()
                .any(|l| l == &format!("PRIVMSG {HANYUU} :.fave 7"))
        );
    }

    #[test]
    fn rizon_quits_not_here_identify_after_001() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut cfg = rizon_config("Geiravor");
        cfg.nickserv_password = "s3cret".into();
        let mut io = ScriptedIo::new(vec![
            "PING :xyz".into(),
            ":irc 001 Geiravor :welcome".into(),
            ":NickServ NOTICE Geiravor :Password accepted".into(),
            format!(":{HANYUU}!b@r NOTICE Geiravor :Added 'A - B' to your favorites."),
        ]);
        let result = run_add_fave(
            &mut io,
            &cfg,
            &tap,
            || "A - B".into(),
            "Geiravor",
            false,
            None,
        )
        .unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert!(io.sent.iter().any(|l| l == "PONG :xyz"));
        let nickserv_at = io
            .sent
            .iter()
            .position(|l| l.contains("NickServ"))
            .expect("identify");
        let welcome_sent_nick = io.sent.iter().position(|l| l == "NICK Geiravor").unwrap();
        assert!(nickserv_at > welcome_sent_nick);
        assert!(
            !io.sent
                .iter()
                .any(|l| l.contains("s3cret") && !l.contains("IDENTIFY"))
        );
    }

    #[test]
    fn nick_mismatch_does_not_fave() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![":irc 001 geiravor_ :welcome".into()]);
        let err = run_add_fave(
            &mut io,
            &rizon_config("Geiravor"),
            &tap,
            || "A - B".into(),
            "Geiravor",
            false,
            None,
        )
        .unwrap_err();
        assert!(err.to_string().contains("nick mismatch"), "{err}");
        assert!(!io.sent.iter().any(|l| l.contains("Hanyuu")));
    }

    #[test]
    fn nicks_match_is_case_insensitive() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":irc 001 geiravor :welcome".into(),
            format!(":{HANYUU}!b@r NOTICE x :Added 'A - B' to your favorites."),
        ]);
        let result = run_add_fave(
            &mut io,
            &rizon_config("Geiravor"),
            &tap,
            || "A - B".into(),
            "Geiravor",
            false,
            None,
        )
        .unwrap();
        assert_eq!(result.kind, FaveKind::Success);
    }

    #[test]
    fn unfave_without_catalog_id_sends_nothing() {
        let tap = TapSnapshot {
            is_afk: false,
            track_id: 99,
            np: "Live - Only".into(),
        };
        let mut io = ScriptedIo::new(vec![":irc 001 geiravor-aaaa :welcome".into()]);
        let result = run_add_fave(
            &mut io,
            &bouncer_config(""),
            &tap,
            || tap.np.clone(),
            "geiravor-aaaa",
            true,
            None,
        )
        .unwrap();
        assert_eq!(result.kind, FaveKind::Failed);
        assert!(!io.sent.iter().any(|l| l.contains("Hanyuu")));
    }

    #[test]
    fn unfave_not_favorited_is_success() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 42,
            np: "Hirasawa Susumu - Gats".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":irc 001 geiravor-aaaa :welcome".into(),
            format!(
                ":{HANYUU}!b@r NOTICE x :You don't have 'Hirasawa Susumu - Gats' in your favorites."
            ),
        ]);
        let result = run_add_fave(
            &mut io,
            &bouncer_config(""),
            &tap,
            || tap.np.clone(),
            "geiravor-aaaa",
            true,
            Some(42),
        )
        .unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert!(!result.favorited);
    }

    #[test]
    fn sasl_plain_waits_for_plus() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":irc CAP * LS :sasl".into(),
            ":irc CAP * ACK :sasl".into(),
            "AUTHENTICATE +".into(),
            ":irc 903 nick :SASL authentication successful".into(),
            ":irc 001 geiravor-aaaa :welcome".into(),
            format!(":{HANYUU}!b@r NOTICE x :Added 'A - B' to your favorites."),
        ]);
        let result = run_add_fave(
            &mut io,
            &sasl_cfg("acct", "secret", ""),
            &tap,
            || "A - B".into(),
            "geiravor-aaaa",
            false,
            None,
        )
        .unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert!(io.sent.iter().any(|l| l == "CAP LS 302"));
        assert!(io.sent.iter().any(|l| l == "AUTHENTICATE PLAIN"));
        let plus = io
            .sent
            .iter()
            .find(|l| l.starts_with("AUTHENTICATE ") && *l != "AUTHENTICATE PLAIN")
            .cloned()
            .unwrap();
        assert!(!plus.contains("secret"));
        assert_eq!(
            plus,
            format!(
                "AUTHENTICATE {}",
                base64::Engine::encode(
                    &base64::engine::general_purpose::STANDARD,
                    b"\0acct\0secret"
                )
            )
        );
    }

    #[test]
    fn sasl_external_and_cert_beats_plain() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":irc CAP * LS :sasl".into(),
            ":irc CAP * ACK :sasl".into(),
            "AUTHENTICATE +".into(),
            ":irc 903 nick :ok".into(),
            ":irc 001 geiravor-aaaa :welcome".into(),
            format!(":{HANYUU}!b@r NOTICE x :Added 'A - B' to your favorites."),
        ]);
        let result = run_add_fave(
            &mut io,
            &sasl_cfg("acct", "secret", "-----BEGIN CERTIFICATE-----"),
            &tap,
            || "A - B".into(),
            "geiravor-aaaa",
            false,
            None,
        )
        .unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert!(io.sent.iter().any(|l| l == "AUTHENTICATE EXTERNAL"));
        assert!(io.sent.iter().any(|l| l == "AUTHENTICATE +"));
        assert!(!io.sent.iter().any(|l| l == "AUTHENTICATE PLAIN"));
    }

    #[test]
    fn sasl_missing_from_cap_does_not_fave() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![":irc CAP * LS :multi-prefix".into()]);
        let err = run_add_fave(
            &mut io,
            &sasl_cfg("acct", "secret", ""),
            &tap,
            || "A - B".into(),
            "geiravor-aaaa",
            false,
            None,
        )
        .unwrap_err();
        assert!(err.to_string().contains("SASL missing"), "{err}");
        assert!(!io.sent.iter().any(|l| l.contains("Hanyuu")));
    }

    #[test]
    fn probe_is_handshake_only() {
        let mut io = ScriptedIo::new(vec![":irc 001 geiravor-aaaa :welcome".into()]);
        run_probe(&mut io, &bouncer_config(""), "geiravor-aaaa").unwrap();
        assert!(
            !io.sent
                .iter()
                .any(|l| l.contains("Hanyuu") || l.contains(".fave"))
        );
        assert!(!io.sent.iter().any(|l| l.starts_with("QUIT")));
    }
}
