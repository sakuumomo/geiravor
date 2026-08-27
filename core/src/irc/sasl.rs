use base64::Engine;
use base64::engine::general_purpose::STANDARD;

use super::FaveConfig;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SaslMechanism {
    Plain { username: String, password: String },
    External,
}

pub fn mechanism(config: &FaveConfig) -> Option<SaslMechanism> {
    if !config.client_cert_pem.trim().is_empty() {
        return Some(SaslMechanism::External);
    }
    let password = config.sasl_password.trim();
    if password.is_empty() {
        return None;
    }
    let username = if config.sasl_username.trim().is_empty() {
        config.nick.trim().to_string()
    } else {
        config.sasl_username.trim().to_string()
    };
    Some(SaslMechanism::Plain {
        username,
        password: password.to_string(),
    })
}

pub fn list_has_sasl(list: &str) -> bool {
    list.split_whitespace().any(|token| {
        token
            .split('=')
            .next()
            .unwrap_or(token)
            .eq_ignore_ascii_case("sasl")
    })
}

pub fn plain_token(username: &str, password: &str) -> String {
    let mut raw = Vec::with_capacity(username.len() + password.len() + 2);
    raw.push(0);
    raw.extend_from_slice(username.as_bytes());
    raw.push(0);
    raw.extend_from_slice(password.as_bytes());
    STANDARD.encode(raw)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::irc::IrcProfile;

    fn cfg(user: &str, pass: &str, cert: &str) -> FaveConfig {
        FaveConfig {
            nick: "Geiravor".into(),
            profile: IrcProfile::Bouncer,
            nickserv_password: String::new(),
            bouncer_host: String::new(),
            bouncer_port: 6697,
            bouncer_pass: String::new(),
            allow_insecure_tls: false,
            sasl_username: user.into(),
            sasl_password: pass.into(),
            client_cert_pem: cert.into(),
            client_key_pem: String::new(),
            tls_fingerprint: String::new(),
        }
    }

    #[test]
    fn cert_is_preferred_over_sasl_password() {
        assert_eq!(
            mechanism(&cfg("acct", "secret", "-----BEGIN CERTIFICATE-----")),
            Some(SaslMechanism::External)
        );
        let m = mechanism(&cfg("acct", "secret", "")).unwrap();
        assert!(matches!(m, SaslMechanism::Plain { ref username, .. } if username == "acct"));
    }

    #[test]
    fn empty_username_uses_public_nick() {
        let m = mechanism(&cfg("", "secret", "")).unwrap();
        match m {
            SaslMechanism::Plain { username, password } => {
                assert_eq!(username, "Geiravor");
                assert_eq!(password, "secret");
            }
            SaslMechanism::External => panic!("expected plain"),
        }
    }

    #[test]
    fn cert_without_password_is_external() {
        assert_eq!(
            mechanism(&cfg("", "", "-----BEGIN CERTIFICATE-----")),
            Some(SaslMechanism::External)
        );
    }

    #[test]
    fn plain_token_is_base64_of_nul_user_nul_pass() {
        assert_eq!(plain_token("acct", "secret"), STANDARD.encode("\0acct\0secret"));
        assert!(!plain_token("acct", "secret").contains("secret"));
    }

    #[test]
    fn cap_list_finds_sasl_among_tokens() {
        assert!(list_has_sasl("multi-prefix sasl=PLAIN,EXTERNAL"));
        assert!(list_has_sasl("SASL"));
        assert!(!list_has_sasl("multi-prefix account-tag"));
    }
}
