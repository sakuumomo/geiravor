//! Handshake + Hanyuu fave machine. Never log secrets.

use std::time::{Duration, Instant};

use super::protocol::{
    assigned_nick_001, hanyuu_added, hanyuu_already, hanyuu_removed, hanyuu_unknown,
    is_hanyuu_notice, is_nick_error, named_song, np_match,
};
use super::tls::{IrcError, TlsIrc};
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

/// Handshake only (Settings → Test connection). No `.fave`.
pub fn run_probe(
    conn: &mut TlsIrc,
    cfg: &FaveConfig,
    expected_nick: &str,
) -> Result<String, IrcError> {
    handshake(conn, cfg, expected_nick)?;
    Ok(conn.server_fingerprint().to_string())
}

/// After TLS is up: handshake, then AFK or live-DJ machine.
pub fn run_add_fave(
    conn: &mut TlsIrc,
    cfg: &FaveConfig,
    tap: &TapSnapshot,
    mut latest_np: impl FnMut() -> String,
    expected_nick: &str,
    unfave: bool,
    catalog_id: Option<i64>,
) -> Result<FaveResult, IrcError> {
    handshake(conn, cfg, expected_nick)?;
    let now = latest_np();
    if tap.is_afk && tap.track_id > 0 {
        afk_id(conn, tap, unfave, catalog_id)
    } else {
        live_dj(conn, tap, &now, unfave)
    }
}

fn handshake(conn: &mut TlsIrc, cfg: &FaveConfig, expected_nick: &str) -> Result<(), IrcError> {
    let bouncer = matches!(cfg.profile, IrcProfile::Bouncer);
    if bouncer && !cfg.bouncer_pass.is_empty() {
        conn.write_line(&format!("PASS {}", cfg.bouncer_pass))?;
    }
    let want_sasl = !cfg.client_cert_pem.trim().is_empty() || !cfg.sasl_password.is_empty();
    if want_sasl {
        conn.write_line("CAP LS 302")?;
    }
    conn.write_line(&format!("NICK {expected_nick}"))?;
    conn.write_line("USER geiravor 0 * :Geiravor")?;
    if want_sasl {
        sasl(conn, cfg)?;
    } else if !bouncer && !cfg.nickserv_password.is_empty() {
        conn.write_line(&format!(
            "PRIVMSG NickServ :IDENTIFY {}",
            cfg.nickserv_password
        ))?;
        wait_quiet(conn, Duration::from_secs(5))?;
    }
    let mut assigned = String::new();
    let deadline = Instant::now() + Duration::from_secs(20);
    while Instant::now() < deadline {
        let line = conn.read_line()?;
        if line.to_ascii_uppercase().starts_with("PING ") {
            let token = line.get(5..).unwrap_or("");
            conn.write_line(&format!("PONG {token}"))?;
            continue;
        }
        if is_nick_error(&line) {
            return Err(IrcError::Protocol("nick rejected".into()));
        }
        if let Some(n) = assigned_nick_001(&line) {
            assigned = n;
            break;
        }
        if line.split_whitespace().nth(1) == Some("NICK") {
            return Err(IrcError::Protocol("server renamed nick".into()));
        }
    }
    if assigned != expected_nick {
        return Err(IrcError::Protocol(format!(
            "nick mismatch: got {assigned}, want {expected_nick}"
        )));
    }
    Ok(())
}

fn sasl(conn: &mut TlsIrc, cfg: &FaveConfig) -> Result<(), IrcError> {
    let mut saw_sasl = false;
    let deadline = Instant::now() + Duration::from_secs(10);
    while Instant::now() < deadline {
        let line = conn.read_line()?;
        if line.contains("CAP") && line.to_ascii_lowercase().contains("sasl") {
            saw_sasl = true;
            break;
        }
        if assigned_nick_001(&line).is_some() {
            return Err(IrcError::Protocol("SASL missing from CAP LS".into()));
        }
    }
    if !saw_sasl {
        return Err(IrcError::Protocol("SASL missing from CAP LS".into()));
    }
    conn.write_line("CAP REQ :sasl")?;
    let external = !cfg.client_cert_pem.trim().is_empty();
    if external {
        conn.write_line("AUTHENTICATE EXTERNAL")?;
        conn.write_line("AUTHENTICATE +")?;
    } else {
        let user = if cfg.sasl_username.trim().is_empty() {
            cfg.nick.trim()
        } else {
            cfg.sasl_username.trim()
        };
        let payload = format!("\0{user}\0{}", cfg.sasl_password);
        let b64 = base64::Engine::encode(
            &base64::engine::general_purpose::STANDARD,
            payload.as_bytes(),
        );
        conn.write_line("AUTHENTICATE PLAIN")?;
        conn.write_line(&format!("AUTHENTICATE {b64}"))?;
    }
    let deadline = Instant::now() + Duration::from_secs(10);
    while Instant::now() < deadline {
        let line = conn.read_line()?;
        match super::protocol::numeric(&line) {
            Some("903") => {
                conn.write_line("CAP END")?;
                return Ok(());
            }
            Some("904" | "905" | "902" | "906" | "907") => {
                return Err(IrcError::Protocol("SASL failed".into()));
            }
            _ => {}
        }
    }
    Err(IrcError::Timeout)
}

fn wait_quiet(conn: &mut TlsIrc, how_long: Duration) -> Result<(), IrcError> {
    let deadline = Instant::now() + how_long;
    conn.tls_set_read_timeout(Duration::from_millis(200))?;
    while Instant::now() < deadline {
        match conn.read_line() {
            Ok(line) if line.to_ascii_lowercase().contains("invalid") => {
                return Err(IrcError::Protocol("NickServ invalid".into()));
            }
            Ok(_) => {}
            Err(IrcError::Timeout) => {}
            Err(e) => return Err(e),
        }
    }
    conn.tls_set_read_timeout(Duration::from_secs(30))?;
    Ok(())
}

fn wait_hanyuu(conn: &mut TlsIrc) -> Result<String, IrcError> {
    let deadline = Instant::now() + Duration::from_secs(15);
    while Instant::now() < deadline {
        let line = conn.read_line()?;
        if line.to_ascii_uppercase().starts_with("PING ") {
            conn.write_line(&format!("PONG {}", line.get(5..).unwrap_or("")))?;
            continue;
        }
        if is_hanyuu_notice(&line) {
            return Ok(line);
        }
    }
    Err(IrcError::Timeout)
}

fn privmsg(conn: &mut TlsIrc, cmd: &str) -> Result<(), IrcError> {
    conn.write_line(&format!("PRIVMSG {HANYUU} :{cmd}"))
}

fn afk_id(
    conn: &mut TlsIrc,
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
        if hanyuu_removed(&notice) {
            return Ok(FaveResult::success(tap.np.clone(), false));
        }
        return Ok(FaveResult::failed(named_song(&notice).unwrap_or_default()));
    }
    if hanyuu_added(&notice) || hanyuu_already(&notice) {
        return Ok(FaveResult::success(tap.np.clone(), true));
    }
    Ok(FaveResult::failed(named_song(&notice).unwrap_or_default()))
}

fn live_dj(
    conn: &mut TlsIrc,
    tap: &TapSnapshot,
    latest: &str,
    unfave: bool,
) -> Result<FaveResult, IrcError> {
    let first = if np_match(latest, &tap.np) {
        if unfave { ".unfave" } else { ".fave" }
    } else if unfave {
        ".unfave last"
    } else {
        ".fave last"
    };
    privmsg(conn, first)?;
    let notice = wait_hanyuu(conn)?;
    if hanyuu_unknown(&notice) {
        return Ok(FaveResult::failed("unknown"));
    }
    let named = named_song(&notice).unwrap_or_default();
    if np_match(&named, &tap.np) || (hanyuu_already(&notice) && np_match(&named, &tap.np)) {
        let fav = !unfave;
        return Ok(FaveResult::success(tap.np.clone(), fav));
    }
    if hanyuu_already(&notice) {
        return Ok(FaveResult::failed(named));
    }
    if !hanyuu_added(&notice) {
        return Ok(FaveResult::failed(named));
    }
    // Added the wrong title: undo, maybe try last.
    if first == ".fave" {
        privmsg(conn, ".unfave")?;
        let _ = wait_hanyuu(conn)?;
        privmsg(conn, ".fave last")?;
        let n2 = wait_hanyuu(conn)?;
        let named2 = named_song(&n2).unwrap_or_default();
        if np_match(&named2, &tap.np) || hanyuu_already(&n2) && np_match(&named2, &tap.np) {
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
