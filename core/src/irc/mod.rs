//! Short-lived TLS IRC to `PRIVMSG Hanyuu-sama`. Not a chat client.
//!
//! Product: [`docs/spec/requests-faves.md`](../../../docs/spec/requests-faves.md).

mod protocol;
mod session;
mod tls;

pub use session::{attach_nick, run_add_fave, run_probe, with_retries};
pub use tls::{IrcError, TlsIrc, certificate_fingerprint_sha256, connect_irc, tcp_connect_timeout};

pub const RIZON_HOST: &str = "irc.rizon.net";
pub const RIZON_PORT: u16 = 6697;
pub const HANYUU: &str = "Hanyuu-sama";
pub const DEFAULT_BOUNCER_PORT: u16 = 6697;

/// Direct Rizon vs already-identified bouncer.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum IrcProfile {
    Rizon,
    Bouncer,
}

/// Secrets are in-memory for this call only. Never log passwords or PEM.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FaveConfig {
    pub nick: String,
    pub list_nick: String,
    pub profile: IrcProfile,
    pub nickserv_password: String,
    pub bouncer_host: String,
    pub bouncer_port: u16,
    pub bouncer_pass: String,
    pub allow_insecure_tls: bool,
    pub sasl_username: String,
    pub sasl_password: String,
    pub client_cert_pem: String,
    pub client_key_pem: String,
    pub tls_fingerprint: String,
}

/// Snapshot at tap. Live DJ must not use leftover AFK `trackid`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TapSnapshot {
    pub is_afk: bool,
    pub track_id: i64,
    pub np: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum FaveKind {
    Noop,
    Success,
    Failed,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FaveResult {
    pub kind: FaveKind,
    pub message: String,
    pub favorited: bool,
}

impl FaveResult {
    pub fn noop() -> Self {
        Self {
            kind: FaveKind::Noop,
            message: String::new(),
            favorited: false,
        }
    }

    pub fn success(message: impl Into<String>, favorited: bool) -> Self {
        Self {
            kind: FaveKind::Success,
            message: message.into(),
            favorited,
        }
    }

    pub fn failed(message: impl Into<String>) -> Self {
        Self {
            kind: FaveKind::Failed,
            message: message.into(),
            favorited: false,
        }
    }
}

/// Empty or whitespace nick is a no-op.
pub fn nick_is_empty(nick: &str) -> bool {
    nick.trim().is_empty()
}

/// IRC nick: Connection, else Favorites list nick.
pub fn irc_nick(cfg: &FaveConfig) -> &str {
    if !nick_is_empty(&cfg.nick) {
        cfg.nick.trim()
    } else {
        cfg.list_nick.trim()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    #[test]
    fn empty_nick() {
        assert!(nick_is_empty("  "));
        assert!(!nick_is_empty("geiravor"));
    }

    #[test]
    fn leftover_address_timeout_is_short() {
        assert_eq!(tcp_connect_timeout(1), Duration::from_secs(2));
        assert_eq!(tcp_connect_timeout(0), Duration::from_secs(10));
    }
}
