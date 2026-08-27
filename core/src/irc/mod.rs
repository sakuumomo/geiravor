mod io;
mod machine;
mod protocol;
mod sasl;
mod session;
mod tls;

pub use machine::TapSnapshot;
pub use session::{attach_nick, run_add_fave, run_probe};
pub use tls::{
    IrcError, TlsIrc, certificate_fingerprint_sha256, connected_message, connect_irc,
};

pub const RIZON_HOST: &str = "irc.rizon.net";
pub const RIZON_PORT: u16 = 6697;
pub const HANYUU: &str = "Hanyuu-sama";
pub const DEFAULT_BOUNCER_PORT: u16 = 6697;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum IrcProfile {
    Rizon,
    Bouncer,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FaveConfig {
    pub nick: String,
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

pub fn nick_is_empty(nick: &str) -> bool {
    nick.trim().is_empty()
}
