//! HTTP/parse failures. Stream-down is a player state, not this type.

/// Transport or decode failure. See `docs/architecture.md`.
#[derive(Debug, Clone, thiserror::Error, uniffi::Error)]
pub enum ApiError {
    #[error("network: {message}")]
    Network { message: String },
    #[error("http {code}")]
    Http { code: u16 },
    #[error("decode: {message}")]
    Decode { message: String },
}
