//! Domain crate for Geiravor.
//!
//! Listener-facing behavior is specified in [`docs/spec/`](../docs/spec/).
//! This crate must not depend on Android types.

/// Cargo package version (`X.Y.Z`). Identical to `versionName` and the User-Agent suffix.
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// HTTP User-Agent: `Geiravor/X.Y.Z`.
pub const USER_AGENT: &str = concat!("Geiravor/", env!("CARGO_PKG_VERSION"));

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_the_rewrite_placeholder() {
        assert_eq!(VERSION, "0.3.0");
    }

    #[test]
    fn user_agent_uses_package_version() {
        assert_eq!(USER_AGENT, concat!("Geiravor/", env!("CARGO_PKG_VERSION")));
    }
}
