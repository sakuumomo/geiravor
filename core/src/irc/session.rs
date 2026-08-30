use std::time::Duration;

use super::io::IrcIo;
use super::machine::{TapSnapshot, run_fave_machine};
use super::protocol::{IrcLine, nicks_match, parse_hanyuu, parse_line};
use super::sasl::{self, SaslMechanism};
use super::{FaveConfig, FaveResult, HANYUU, IrcError, IrcProfile};

pub fn wait_hanyuu<I: IrcIo>(io: &mut I) -> Result<super::protocol::HanyuuReply, IrcError> {
    loop {
        let raw = io.recv()?;
        match parse_line(&raw) {
            IrcLine::Ping(token) => io.send(&format!("PONG :{token}"))?,
            IrcLine::Notice { from, text } | IrcLine::Privmsg { from, text }
                if from.eq_ignore_ascii_case(HANYUU) =>
            {
                return Ok(parse_hanyuu(&text));
            }
            _ => {}
        }
    }
}

pub fn handshake<I: IrcIo>(
    io: &mut I,
    config: &FaveConfig,
    attach_nick: &str,
) -> Result<(), IrcError> {
    let sasl = sasl::mechanism(config);
    if config.profile == IrcProfile::Bouncer && !config.bouncer_pass.trim().is_empty() {
        io.send(&format!("PASS {}", config.bouncer_pass.trim()))?;
    }
    if sasl.is_some() {
        io.send("CAP LS 302")?;
    }
    io.send(&format!("NICK {attach_nick}"))?;
    io.send("USER geiravor 0 * :Geiravor")?;
    let mut welcomed = false;
    let mut sasl_done = sasl.is_none();
    let mut saw_sasl_cap = false;
    let mut cap_req_sent = false;
    while !(welcomed && sasl_done) {
        let raw = io.recv()?;
        match parse_line(&raw) {
            IrcLine::Ping(token) => io.send(&format!("PONG :{token}"))?,
            IrcLine::CapLs { continued, list } => {
                if sasl::list_has_sasl(&list) {
                    saw_sasl_cap = true;
                }
                if sasl.is_some() && saw_sasl_cap && !cap_req_sent {
                    cap_req_sent = true;
                    io.send("CAP REQ :sasl")?;
                } else if sasl.is_some() && !continued && !saw_sasl_cap {
                    return Err(IrcError::Sasl {
                        detail: "Server does not support SASL.".into(),
                    });
                }
            }
            IrcLine::CapAck => match &sasl {
                Some(SaslMechanism::Plain { .. }) => io.send("AUTHENTICATE PLAIN")?,
                Some(SaslMechanism::External) => io.send("AUTHENTICATE EXTERNAL")?,
                None => {}
            },
            IrcLine::CapNak => {
                return Err(IrcError::Sasl {
                    detail: "SASL was rejected.".into(),
                });
            }
            IrcLine::Authenticate(token) if token == "+" => match &sasl {
                Some(SaslMechanism::Plain { username, password }) => {
                    io.send(&format!("AUTHENTICATE {}", sasl::plain_token(username, password)))?;
                }
                Some(SaslMechanism::External) => io.send("AUTHENTICATE +")?,
                None => {}
            },
            IrcLine::SaslSuccess => {
                sasl_done = true;
                io.send("CAP END")?;
            }
            IrcLine::SaslFail => {
                let _ = io.send("CAP END");
                return Err(IrcError::Sasl {
                    detail: "SASL authentication failed.".into(),
                });
            }
            IrcLine::Welcome { nick } => {
                if !nicks_match(&nick, attach_nick) {
                    return Err(IrcError::NickMismatch {
                        wanted: attach_nick.into(),
                        got: nick,
                    });
                }
                welcomed = true;
            }
            IrcLine::NickChange { from, to } => {
                if nicks_match(&from, attach_nick) && !nicks_match(&to, attach_nick) {
                    return Err(IrcError::NickMismatch {
                        wanted: attach_nick.into(),
                        got: to,
                    });
                }
            }
            IrcLine::NickInUse => {
                return Err(IrcError::NickInUse {
                    detail: attach_nick.into(),
                });
            }
            _ => {}
        }
    }
    if sasl.is_none()
        && config.profile == IrcProfile::Rizon
        && !config.nickserv_password.trim().is_empty()
    {
        io.send(&format!(
            "PRIVMSG NickServ :IDENTIFY {}",
            config.nickserv_password.trim()
        ))?;
        io.set_read_timeout(Duration::from_secs(5))?;
        match wait_nickserv(io) {
            Ok(text) if text.to_ascii_lowercase().contains("invalid password") => {
                return Err(IrcError::NickServ {
                    detail: "Invalid NickServ password".into(),
                });
            }
            Ok(_) | Err(IrcError::Timeout { .. }) => {}
            Err(e) => return Err(e),
        }
        io.set_read_timeout(Duration::from_secs(20))?;
    }
    Ok(())
}

fn wait_nickserv<I: IrcIo>(io: &mut I) -> Result<String, IrcError> {
    loop {
        let raw = io.recv()?;
        match parse_line(&raw) {
            IrcLine::Ping(token) => io.send(&format!("PONG :{token}"))?,
            IrcLine::Notice { from, text } if from.to_ascii_lowercase().contains("nickserv") => {
                return Ok(text);
            }
            _ => {}
        }
    }
}

pub fn finish<I: IrcIo>(io: &mut I, profile: IrcProfile) -> Result<(), IrcError> {
    if profile == IrcProfile::Rizon {
        io.send("QUIT :Geiravor")?;
    }
    Ok(())
}

pub fn run_add_fave<I: IrcIo>(
    io: &mut I,
    config: &FaveConfig,
    tap: &TapSnapshot,
    latest_np: impl Fn() -> String,
    attach_nick: &str,
    unfave: bool,
    catalog_id: Option<i64>,
) -> Result<FaveResult, IrcError> {
    handshake(io, config, attach_nick)?;
    let result = run_fave_machine(io, tap, latest_np, unfave, catalog_id)?;
    finish(io, config.profile)?;
    Ok(result)
}

pub fn run_probe<I: IrcIo>(
    io: &mut I,
    config: &FaveConfig,
    attach_nick: &str,
) -> Result<(), IrcError> {
    handshake(io, config, attach_nick)?;
    finish(io, config.profile)?;
    Ok(())
}

pub fn attach_nick(profile: IrcProfile, public_nick: &str) -> String {
    match profile {
        IrcProfile::Rizon => public_nick.trim().to_string(),
        IrcProfile::Bouncer => {
            let n = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.subsec_nanos())
                .unwrap_or(1);
            format!("geiravor-{:04x}", (n >> 16) as u16)
        }
    }
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

    #[test]
    fn bouncer_no_quit_no_nick_after_001_no_nickserv() {
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
        assert_eq!(io.sent[2], "USER geiravor 0 * :Geiravor");
        assert!(io.sent.iter().any(|l| l == &format!("PRIVMSG {HANYUU} :.fave 7")));
        assert!(!io.sent.iter().any(|l| l.starts_with("QUIT")));
        assert!(!io.sent.iter().any(|l| l.contains("NickServ")));
        assert!(!io.sent.iter().any(|l| l == "NICK Geiravor"));
        let nicks: Vec<_> = io
            .sent
            .iter()
            .filter(|l| l.starts_with("NICK "))
            .collect();
        assert_eq!(nicks.len(), 1);
    }

    #[test]
    fn rizon_quits_after_fave() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            "PING :xyz".into(),
            ":irc 001 Geiravor :welcome".into(),
            format!(":{HANYUU}!b@r NOTICE Geiravor :Added 'A - B' to your favorites."),
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
        assert!(io.sent.iter().any(|l| l == "PONG :xyz"));
        assert_eq!(io.sent.last().unwrap(), "QUIT :Geiravor");
    }

    #[test]
    fn probe_handshakes_without_privmsg() {
        let mut io = ScriptedIo::new(vec![":irc 001 Geiravor :welcome".into()]);
        run_probe(&mut io, &rizon_config("Geiravor"), "Geiravor").unwrap();
        assert!(!io.sent.iter().any(|l| l.contains("PRIVMSG")));
        assert_eq!(io.sent.last().unwrap(), "QUIT :Geiravor");
    }

    #[test]
    fn nick_in_use_fails_without_ghost() {
        let mut io = ScriptedIo::new(vec![":irc 433 * Geiravor :Nickname is already in use.".into()]);
        let err = handshake(&mut io, &rizon_config("Geiravor"), "Geiravor").unwrap_err();
        assert!(matches!(err, IrcError::NickInUse { .. }));
        assert!(!io.sent.iter().any(|l| l.contains("GHOST") || l.contains("Geiravor_")));
    }

    #[test]
    fn welcome_as_renamed_nick_does_not_fave() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":irc 001 Geiravor_ :welcome".into(),
            format!(":{HANYUU}!b@r NOTICE Geiravor_ :Added 'A - B' to your favorites."),
        ]);
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
        assert!(matches!(
            err,
            IrcError::NickMismatch {
                ref wanted,
                ref got
            } if wanted == "Geiravor" && got == "Geiravor_"
        ));
        assert!(!io.sent.iter().any(|l| l.contains("PRIVMSG")));
        assert!(!io.sent.iter().any(|l| l.contains("Geiravor_")));
    }

    #[test]
    fn forced_nick_underscore_does_not_fave() {
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":Geiravor!u@h NICK :Geiravor_".into(),
            ":irc 001 Geiravor_ :welcome".into(),
        ]);
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
        assert!(matches!(err, IrcError::NickMismatch { .. }));
        assert!(!io.sent.iter().any(|l| l.contains("PRIVMSG")));
    }

    #[test]
    fn sasl_plain_authenticates_without_logging_password_on_nick() {
        let mut cfg = rizon_config("Geiravor");
        cfg.sasl_username = "acct".into();
        cfg.sasl_password = "s3cret".into();
        let tap = TapSnapshot {
            is_afk: true,
            track_id: 1,
            np: "A - B".into(),
        };
        let mut io = ScriptedIo::new(vec![
            ":irc CAP * LS :sasl".into(),
            ":irc CAP * ACK :sasl".into(),
            "AUTHENTICATE +".into(),
            ":irc 903 Geiravor :SASL authentication successful".into(),
            ":irc 001 Geiravor :welcome".into(),
            format!(":{HANYUU}!b@r NOTICE Geiravor :Added 'A - B' to your favorites."),
        ]);
        let result = run_add_fave(&mut io, &cfg, &tap, || "A - B".into(), "Geiravor", false, None)
            .unwrap();
        assert_eq!(result.kind, FaveKind::Success);
        assert_eq!(io.sent[0], "CAP LS 302");
        assert!(io.sent.iter().any(|l| l == "CAP REQ :sasl"));
        assert!(io.sent.iter().any(|l| l == "AUTHENTICATE PLAIN"));
        let token = format!("AUTHENTICATE {}", sasl::plain_token("acct", "s3cret"));
        assert!(io.sent.iter().any(|l| l == &token));
        assert!(!io.sent.iter().any(|l| l.contains("s3cret") && !l.starts_with("AUTHENTICATE ")));
        assert!(!io.sent.iter().any(|l| l.contains("NickServ")));
        assert!(io.sent.iter().any(|l| l == "CAP END"));
    }

    #[test]
    fn sasl_external_sends_empty_authenticate() {
        let mut cfg = rizon_config("Geiravor");
        cfg.client_cert_pem = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----".into();
        let mut io = ScriptedIo::new(vec![
            ":irc CAP * LS :multi-prefix sasl=EXTERNAL,PLAIN".into(),
            ":irc CAP * ACK :sasl".into(),
            "AUTHENTICATE +".into(),
            ":irc 903 Geiravor :ok".into(),
            ":irc 001 Geiravor :welcome".into(),
        ]);
        handshake(&mut io, &cfg, "Geiravor").unwrap();
        assert!(io.sent.iter().any(|l| l == "AUTHENTICATE EXTERNAL"));
        assert_eq!(
            io.sent.iter().filter(|l| *l == "AUTHENTICATE +").count(),
            1
        );
        assert!(!io.sent.iter().any(|l| l == "AUTHENTICATE PLAIN"));
    }

    #[test]
    fn pem_prefers_external_over_plain() {
        let mut cfg = rizon_config("Geiravor");
        cfg.sasl_username = "acct".into();
        cfg.sasl_password = "s3cret".into();
        cfg.client_cert_pem = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----".into();
        let mut io = ScriptedIo::new(vec![
            ":irc CAP * LS :sasl=PLAIN,EXTERNAL".into(),
            ":irc CAP * ACK :sasl".into(),
            "AUTHENTICATE +".into(),
            ":irc 903 Geiravor :ok".into(),
            ":irc 001 Geiravor :welcome".into(),
        ]);
        handshake(&mut io, &cfg, "Geiravor").unwrap();
        assert!(io.sent.iter().any(|l| l == "AUTHENTICATE EXTERNAL"));
        assert!(!io.sent.iter().any(|l| l == "AUTHENTICATE PLAIN"));
        assert!(!io.sent.iter().any(|l| l.contains("s3cret")));
    }

    #[test]
    fn sasl_missing_from_cap_does_not_fave() {
        let mut cfg = rizon_config("Geiravor");
        cfg.sasl_password = "s3cret".into();
        let mut io = ScriptedIo::new(vec![":irc CAP * LS :multi-prefix".into()]);
        let err = handshake(&mut io, &cfg, "Geiravor").unwrap_err();
        assert!(matches!(err, IrcError::Sasl { .. }));
        assert!(!io.sent.iter().any(|l| l.contains("PRIVMSG")));
        assert!(!io.sent.iter().any(|l| l.contains("AUTHENTICATE")));
    }
}
