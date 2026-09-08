//! rustls client. IPv6 first; leftover addresses use a short connect timeout.

use std::io::{BufRead, BufReader, Write};
use std::net::{Shutdown, TcpStream, ToSocketAddrs};
use std::sync::Arc;
use std::time::Duration;

use rustls::pki_types::{CertificateDer, PrivateKeyDer, ServerName};
use rustls::{ClientConfig, ClientConnection, RootCertStore, StreamOwned};

/// IRC transport failure. Timeout/Network/Tls are retryable.
#[derive(Debug, Clone, thiserror::Error)]
pub enum IrcError {
    #[error("network: {0}")]
    Network(String),
    #[error("timeout")]
    Timeout,
    #[error("tls: {0}")]
    Tls(String),
    #[error("fingerprint mismatch")]
    Fingerprint,
    #[error("{0}")]
    Protocol(String),
}

impl IrcError {
    pub fn retryable(&self) -> bool {
        matches!(self, Self::Network(_) | Self::Timeout | Self::Tls(_))
    }
}

const LAST_TIMEOUT: Duration = Duration::from_secs(10);
const LEFTOVER_TIMEOUT: Duration = Duration::from_secs(2);

/// `remaining` is how many addresses are left **after** this one.
pub fn tcp_connect_timeout(remaining: usize) -> Duration {
    if remaining > 0 {
        LEFTOVER_TIMEOUT
    } else {
        LAST_TIMEOUT
    }
}

/// SHA-256 of the first certificate in a PEM blob (CertFP display).
#[uniffi::export]
pub fn certificate_fingerprint_sha256(pem: String) -> String {
    let mut cursor = std::io::Cursor::new(pem.into_bytes());
    let Ok(certs) = rustls_pemfile::certs(&mut cursor).collect::<Result<Vec<_>, _>>() else {
        return String::new();
    };
    let Some(cert) = certs.first() else {
        return String::new();
    };
    fingerprint_der(cert.as_ref())
}

fn fingerprint_der(der: &[u8]) -> String {
    let hash = ring::digest::digest(&ring::digest::SHA256, der);
    hash.as_ref()
        .iter()
        .map(|b| format!("{b:02X}"))
        .collect::<Vec<_>>()
        .join(":")
}

fn normalize_fp(s: &str) -> String {
    s.chars()
        .filter(|c| c.is_ascii_hexdigit())
        .map(|c| c.to_ascii_uppercase())
        .collect()
}

/// Host field may include `:port` (and `[ipv6]:port`). Hostname is TLS SNI.
pub fn parse_host_port(host: &str, port: u16) -> (String, u16) {
    let h = host.trim();
    if h.is_empty() {
        return (String::new(), port);
    }
    if let Some(rest) = h.strip_prefix('[')
        && let Some((ip, tail)) = rest.split_once(']')
    {
        let p = tail
            .strip_prefix(':')
            .and_then(|s| s.parse().ok())
            .filter(|n: &u16| *n > 0)
            .unwrap_or(port);
        return (ip.to_string(), p);
    }
    if let Some((name, pstr)) = h.rsplit_once(':')
        && !name.is_empty()
        && !name.contains(':')
        && let Ok(p) = pstr.parse::<u16>()
        && p > 0
    {
        return (name.to_string(), p);
    }
    (h.to_string(), port)
}

/// Open TLS to `host:port`. `insecure` is bouncer-only at the call site.
pub fn connect_irc(
    host: &str,
    port: u16,
    insecure: bool,
    client_cert_pem: &str,
    client_key_pem: &str,
    tls_fingerprint: &str,
) -> Result<TlsIrc, IrcError> {
    let _ = rustls::crypto::ring::default_provider().install_default();
    let (host, port) = parse_host_port(host, port);
    if host.is_empty() {
        return Err(IrcError::Protocol("bouncer host required".into()));
    }
    let mut tcp = tcp_connect(&host, port)?;
    tcp.set_read_timeout(Some(Duration::from_secs(30))).ok();
    tcp.set_write_timeout(Some(Duration::from_secs(30))).ok();

    let pin = !tls_fingerprint.trim().is_empty();
    let verifier = Arc::new(PinVerifier {
        insecure,
        want: normalize_fp(tls_fingerprint),
    });
    let config = if insecure || pin {
        let b = ClientConfig::builder()
            .dangerous()
            .with_custom_certificate_verifier(verifier);
        finish_client_config(b, client_cert_pem, client_key_pem)?
    } else {
        let mut roots = RootCertStore::empty();
        roots.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
        let b = ClientConfig::builder().with_root_certificates(roots);
        finish_client_config(b, client_cert_pem, client_key_pem)?
    };

    let server_name = ServerName::try_from(host).map_err(|e| IrcError::Tls(e.to_string()))?;
    let mut conn = ClientConnection::new(Arc::new(config), server_name)
        .map_err(|e| IrcError::Tls(e.to_string()))?;
    while conn.is_handshaking() {
        conn.complete_io(&mut tcp)
            .map_err(|e| IrcError::Tls(e.to_string()))?;
    }
    let fp = conn
        .peer_certificates()
        .and_then(|c| c.first())
        .map(|c| fingerprint_der(c.as_ref()))
        .unwrap_or_default();
    if !tls_fingerprint.trim().is_empty() && normalize_fp(&fp) != normalize_fp(tls_fingerprint) {
        return Err(IrcError::Fingerprint);
    }
    Ok(TlsIrc {
        reader: BufReader::new(StreamOwned::new(conn, tcp)),
        fingerprint: fp,
    })
}

fn finish_client_config(
    builder: rustls::ConfigBuilder<ClientConfig, rustls::client::WantsClientCert>,
    client_cert_pem: &str,
    client_key_pem: &str,
) -> Result<ClientConfig, IrcError> {
    let mut config = if client_cert_pem.trim().is_empty() {
        builder.with_no_client_auth()
    } else {
        let (certs, key) = parse_client_pem(client_cert_pem, client_key_pem)?;
        builder
            .with_client_auth_cert(certs, key)
            .map_err(|e| IrcError::Tls(e.to_string()))?
    };
    config.resumption = rustls::client::Resumption::disabled();
    Ok(config)
}

fn parse_client_pem(
    cert_pem: &str,
    key_pem: &str,
) -> Result<(Vec<CertificateDer<'static>>, PrivateKeyDer<'static>), IrcError> {
    let mut cert_cur = std::io::Cursor::new(cert_pem.as_bytes());
    let certs = rustls_pemfile::certs(&mut cert_cur)
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| IrcError::Tls(e.to_string()))?;
    if certs.is_empty() {
        return Err(IrcError::Tls("no client certificate".into()));
    }
    let key_src = if key_pem.trim().is_empty() {
        cert_pem
    } else {
        key_pem
    };
    let mut key_cur = std::io::Cursor::new(key_src.as_bytes());
    let key = rustls_pemfile::private_key(&mut key_cur)
        .map_err(|e| IrcError::Tls(e.to_string()))?
        .ok_or_else(|| IrcError::Tls("no client key".into()))?;
    Ok((certs, key))
}

fn tcp_connect(host: &str, port: u16) -> Result<TcpStream, IrcError> {
    let mut addrs: Vec<_> = (host, port)
        .to_socket_addrs()
        .map_err(|e| IrcError::Network(e.to_string()))?
        .collect();
    addrs.sort_by_key(|a| a.is_ipv4()); // IPv6 first
    if addrs.is_empty() {
        return Err(IrcError::Network("no addresses".into()));
    }
    let last = addrs.len() - 1;
    let mut last_err = IrcError::Network("connect failed".into());
    for (i, addr) in addrs.iter().enumerate() {
        let timeout = tcp_connect_timeout(last - i);
        match TcpStream::connect_timeout(addr, timeout) {
            Ok(s) => return Ok(s),
            Err(e) => {
                last_err = if e.kind() == std::io::ErrorKind::TimedOut {
                    IrcError::Timeout
                } else {
                    IrcError::Network(e.to_string())
                };
            }
        }
    }
    Err(last_err)
}

#[derive(Debug)]
struct PinVerifier {
    insecure: bool,
    want: String,
}

impl rustls::client::danger::ServerCertVerifier for PinVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp: &[u8],
        _now: rustls::pki_types::UnixTime,
    ) -> Result<rustls::client::danger::ServerCertVerified, rustls::Error> {
        let got = normalize_fp(&fingerprint_der(end_entity.as_ref()));
        if !self.want.is_empty() && got != self.want {
            return Err(rustls::Error::General("fingerprint mismatch".into()));
        }
        if self.insecure || !self.want.is_empty() {
            Ok(rustls::client::danger::ServerCertVerified::assertion())
        } else {
            Err(rustls::Error::General("not verified".into()))
        }
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls12_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        rustls::crypto::ring::default_provider()
            .signature_verification_algorithms
            .supported_schemes()
    }
}

/// TLS IRC stream. `close_notify` is orderly detach (bouncer): TLS then TCP FIN.
pub struct TlsIrc {
    reader: BufReader<StreamOwned<ClientConnection, TcpStream>>,
    fingerprint: String,
}

impl TlsIrc {
    pub fn server_fingerprint(&self) -> &str {
        &self.fingerprint
    }

    pub fn write_line(&mut self, line: &str) -> Result<(), IrcError> {
        if line.to_ascii_uppercase().contains("IDENTIFY")
            || line.starts_with("PASS ")
            || line.starts_with("AUTHENTICATE ")
        {
            tracing::debug!("irc write (redacted)");
        } else {
            tracing::debug!(line, "irc write");
        }
        let stream = self.reader.get_mut();
        stream
            .write_all(line.as_bytes())
            .and_then(|_| stream.write_all(b"\r\n"))
            .and_then(|_| stream.flush())
            .map_err(|e| IrcError::Network(e.to_string()))
    }

    pub fn read_line(&mut self) -> Result<String, IrcError> {
        let mut line = String::new();
        let n = self.reader.read_line(&mut line).map_err(|e| {
            if e.kind() == std::io::ErrorKind::WouldBlock
                || e.kind() == std::io::ErrorKind::TimedOut
            {
                IrcError::Timeout
            } else {
                IrcError::Network(e.to_string())
            }
        })?;
        if n == 0 {
            return Err(IrcError::Network("eof".into()));
        }
        if line.len() > 8192 {
            return Err(IrcError::Network("line too long".into()));
        }
        while line.ends_with('\n') || line.ends_with('\r') {
            line.pop();
        }
        Ok(line)
    }

    pub fn close_notify(&mut self) -> Result<(), IrcError> {
        self.reader.get_mut().conn.send_close_notify();
        let _ = self.reader.get_mut().flush();
        let _ = self.reader.get_ref().sock.shutdown(Shutdown::Both);
        Ok(())
    }
}

impl super::io::IrcIo for TlsIrc {
    fn send(&mut self, line: &str) -> Result<(), IrcError> {
        self.write_line(line)
    }

    fn recv(&mut self) -> Result<String, IrcError> {
        self.read_line()
    }

    fn set_read_timeout(&mut self, d: Duration) -> Result<(), IrcError> {
        self.reader
            .get_ref()
            .sock
            .set_read_timeout(Some(d))
            .map_err(|e| IrcError::Network(e.to_string()))
    }
}

impl From<rustls::Error> for IrcError {
    fn from(e: rustls::Error) -> Self {
        let s = e.to_string();
        if s.contains("fingerprint") {
            IrcError::Fingerprint
        } else {
            IrcError::Tls(s)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::parse_host_port;

    #[test]
    fn host_can_include_port() {
        assert_eq!(
            parse_host_port("bouncer.example.com:12329", 6697),
            ("bouncer.example.com".into(), 12329)
        );
        assert_eq!(
            parse_host_port("example.com", 6697),
            ("example.com".into(), 6697)
        );
        assert_eq!(
            parse_host_port("[2001:db8::1]:12329", 6697),
            ("2001:db8::1".into(), 12329)
        );
        assert_eq!(
            parse_host_port("2001:db8::1", 12329),
            ("2001:db8::1".into(), 12329)
        );
        assert_eq!(parse_host_port("  ", 6697), (String::new(), 6697));
    }
}
