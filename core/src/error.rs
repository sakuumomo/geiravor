//! HTTP/parse failures. Stream-down is a player state, not this type.

/// Transport or decode failure. See `docs/architecture.md`.
///
/// The string field is `detail`, not `message`, so UniFFI Kotlin does not
/// clash with `Throwable.message`.
#[derive(Debug, Clone, thiserror::Error, uniffi::Error)]
pub enum ApiError {
    #[error("network: {detail}")]
    Network { detail: String },
    #[error("http {code}")]
    Http { code: u16 },
    #[error("decode: {detail}")]
    Decode { detail: String },
}
