use std::io::{BufRead, BufReader, Write};
use std::net::{Shutdown, SocketAddr, TcpStream};
use std::sync::Arc;
use std::time::Duration;

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::pki_types::{CertificateDer, ServerName, UnixTime};
use rustls::{ClientConfig, ClientConnection, DigitallySignedStruct, RootCertStore, SignatureScheme, StreamOwned};

use super::io::IrcIo;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum IrcError {
    Network { detail: String },
    Tls { detail: String },
    Timeout { detail: String },
    NickInUse { detail: String },
    NickMismatch { wanted: String, got: String },
    NickServ { detail: String },
    Sasl { detail: String },
    Protocol { detail: String },
}

impl std::fmt::Display for IrcError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Network { detail } => write!(f, "network: {detail}"),
            Self::Tls { detail } => write!(f, "tls: {detail}"),
            Self::Timeout { detail } => write!(f, "timeout: {detail}"),
            Self::NickInUse { detail } => write!(f, "nick in use: {detail}"),
            Self::NickMismatch { wanted, got } => {
                write!(f, "nick mismatch: wanted {wanted}, got {got}")
            }
            Self::NickServ { detail } => write!(f, "nickserv: {detail}"),
            Self::Sasl { detail } => write!(f, "sasl: {detail}"),
            Self::Protocol { detail } => write!(f, "irc: {detail}"),
        }
    }
}

impl std::error::Error for IrcError {}

impl IrcError {
    pub fn user_message(&self) -> String {
        match self {
            Self::NickInUse { .. } => "That nick is already in use.".into(),
            Self::NickMismatch { wanted, got } => {
                format!("Connected as {got}, not {wanted}. Fave cancelled.")
            }
            Self::NickServ { detail } => detail.clone(),
            Self::Sasl { detail } => detail.clone(),
            Self::Timeout { .. } => "Timed out talking to IRC.".into(),
            Self::Tls { detail } => format!("TLS: {detail}"),
            Self::Network { detail } => format!("Could not connect: {}", friendly_io_detail(detail)),
            Self::Protocol { detail } => detail.clone(),
        }
    }
}

fn install_ring() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

pub fn parse_host_port(host: &str, port: u16) -> (String, u16) {
    let h = host.trim();
    if let Some(rest) = h.strip_prefix('[') {
        if let Some((ip, tail)) = rest.split_once(']') {
            let p = tail
                .strip_prefix(':')
                .and_then(|s| s.parse().ok())
                .filter(|n: &u16| *n > 0)
                .unwrap_or(port);
            return (ip.to_string(), p);
        }
    }
    if let Some((name, pstr)) = h.rsplit_once(':') {
        if !name.is_empty() && !name.contains(':') {
            if let Ok(p) = pstr.parse::<u16>() {
                if p > 0 {
                    return (name.to_string(), p);
                }
            }
        }
    }
    (h.to_string(), port)
}

fn ipv6_then_ipv4(addrs: impl IntoIterator<Item = SocketAddr>) -> Vec<SocketAddr> {
    let mut v4 = Vec::new();
    let mut v6 = Vec::new();
    for addr in addrs {
        if addr.is_ipv4() {
            v4.push(addr);
        } else {
            v6.push(addr);
        }
    }
    v6.extend(v4);
    v6
}

fn io_detail(err: &std::io::Error) -> String {
    match err.raw_os_error() {
        Some(101) | Some(51) => "network unreachable (no route, often IPv6)".into(),
        Some(113) | Some(65) => "no route to host".into(),
        Some(111) | Some(61) => "connection refused".into(),
        Some(110) | Some(60) => "timed out".into(),
        Some(97) | Some(47) => "address family not supported".into(),
        _ => err.to_string(),
    }
}

fn friendly_io_detail(detail: &str) -> String {
    if detail.contains("os error 101") {
        format!("{detail} (network unreachable; often IPv6 with no route)")
    } else {
        detail.to_string()
    }
}

fn tcp_connect(host: &str, port: u16) -> Result<TcpStream, IrcError> {
    use std::net::ToSocketAddrs;
    let (host, port) = parse_host_port(host, port);
    let addrs = ipv6_then_ipv4(
        (host.as_str(), port)
            .to_socket_addrs()
            .map_err(|e| IrcError::Network {
                detail: e.to_string(),
            })?,
    );
    if addrs.is_empty() {
        return Err(IrcError::Network {
            detail: format!("no addresses for {host}"),
        });
    }
    let mut failures = Vec::new();
    for addr in addrs {
        match TcpStream::connect_timeout(&addr, Duration::from_secs(10)) {
            Ok(stream) => return Ok(stream),
            Err(e) => failures.push(format!("{addr}: {}", io_detail(&e))),
        }
    }
    Err(IrcError::Network {
        detail: format!("{host}:{port} {}", failures.join("; ")),
    })
}

fn tls_server_name(host: &str) -> Result<ServerName<'static>, IrcError> {
    ServerName::try_from(host.to_string()).map_err(|e| IrcError::Tls {
        detail: e.to_string(),
    })
}

pub fn normalize_fingerprint(value: &str) -> String {
    value
        .chars()
        .filter(|c| c.is_ascii_hexdigit())
        .map(|c| c.to_ascii_uppercase())
        .collect()
}

pub fn fingerprints_equal(expected: &str, actual: &str) -> bool {
    let want = normalize_fingerprint(expected);
    want.is_empty() || want == normalize_fingerprint(actual)
}

pub fn fingerprint_sha256(der: &[u8]) -> String {
    let digest = ring::digest::digest(&ring::digest::SHA256, der);
    digest
        .as_ref()
        .iter()
        .map(|b| format!("{b:02X}"))
        .collect::<Vec<_>>()
        .join(":")
}

pub fn certificate_fingerprint_sha256(pem: &str) -> String {
    let mut certs = Vec::new();
    let mut key = None;
    if read_pem(pem, &mut certs, &mut key).is_err() {
        return String::new();
    }
    certs
        .first()
        .map(|c| fingerprint_sha256(c.as_ref()))
        .unwrap_or_default()
}

pub fn connected_message(fingerprint: &str) -> String {
    if fingerprint.is_empty() {
        "Connected.".into()
    } else {
        format!("Connected.\nSHA-256 {fingerprint}")
    }
}

pub fn connect_irc(
    host: &str,
    port: u16,
    allow_insecure: bool,
    client_cert_pem: &str,
    client_key_pem: &str,
    expected_fingerprint: &str,
) -> Result<TlsIrc, IrcError> {
    install_ring();
    let (host, port) = parse_host_port(host, port);
    let mut stream = tcp_connect(&host, port)?;
    stream
        .set_read_timeout(Some(Duration::from_secs(20)))
        .map_err(|e| IrcError::Network {
            detail: e.to_string(),
        })?;
    stream
        .set_write_timeout(Some(Duration::from_secs(10)))
        .map_err(|e| IrcError::Network {
            detail: e.to_string(),
        })?;
    let config = client_config(allow_insecure, client_cert_pem, client_key_pem)?;
    let name = tls_server_name(&host)?;
    let mut conn = ClientConnection::new(config, name).map_err(|e| IrcError::Tls {
        detail: e.to_string(),
    })?;
    while conn.is_handshaking() {
        conn.complete_io(&mut stream).map_err(|e| IrcError::Tls {
            detail: e.to_string(),
        })?;
    }
    let server_fingerprint = conn
        .peer_certificates()
        .and_then(|certs| certs.first())
        .map(|cert| fingerprint_sha256(cert.as_ref()))
        .unwrap_or_default();
    if !fingerprints_equal(expected_fingerprint, &server_fingerprint) {
        return Err(IrcError::Tls {
            detail: format!("fingerprint mismatch (got {server_fingerprint})"),
        });
    }
    Ok(TlsIrc {
        reader: BufReader::new(StreamOwned::new(conn, stream)),
        server_fingerprint,
    })
}

fn finish_client_config(mut config: ClientConfig) -> Arc<ClientConfig> {
    config.resumption = rustls::client::Resumption::disabled();
    Arc::new(config)
}

fn client_config(
    allow_insecure: bool,
    client_cert_pem: &str,
    client_key_pem: &str,
) -> Result<Arc<ClientConfig>, IrcError> {
    let identity = parse_client_identity(client_cert_pem, client_key_pem)?;
    if allow_insecure {
        let builder = ClientConfig::builder()
            .dangerous()
            .with_custom_certificate_verifier(Arc::new(AcceptAny));
        let config = match identity {
            Some((certs, key)) => builder
                .with_client_auth_cert(certs, key)
                .map_err(|e| IrcError::Tls {
                    detail: e.to_string(),
                })?,
            None => builder.with_no_client_auth(),
        };
        return Ok(finish_client_config(config));
    }
    let mut roots = RootCertStore::empty();
    roots.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    let builder = ClientConfig::builder().with_root_certificates(roots);
    let config = match identity {
        Some((certs, key)) => builder
            .with_client_auth_cert(certs, key)
            .map_err(|e| IrcError::Tls {
                detail: e.to_string(),
            })?,
        None => builder.with_no_client_auth(),
    };
    Ok(finish_client_config(config))
}

fn parse_client_identity(
    cert_pem: &str,
    key_pem: &str,
) -> Result<Option<(Vec<CertificateDer<'static>>, rustls::pki_types::PrivateKeyDer<'static>)>, IrcError>
{
    if cert_pem.trim().is_empty() && key_pem.trim().is_empty() {
        return Ok(None);
    }
    let mut certs = Vec::new();
    let mut key = None;
    read_pem(cert_pem, &mut certs, &mut key)?;
    if !key_pem.trim().is_empty() {
        read_pem(key_pem, &mut certs, &mut key)?;
    }
    let key = key.ok_or_else(|| IrcError::Tls {
        detail: "No private key in PEM.".into(),
    })?;
    if certs.is_empty() {
        return Err(IrcError::Tls {
            detail: "No certificate in PEM.".into(),
        });
    }
    Ok(Some((certs, key)))
}

fn read_pem(
    pem: &str,
    certs: &mut Vec<CertificateDer<'static>>,
    key: &mut Option<rustls::pki_types::PrivateKeyDer<'static>>,
) -> Result<(), IrcError> {
    let mut cursor = std::io::Cursor::new(pem.as_bytes());
    for item in rustls_pemfile::read_all(&mut cursor) {
        match item.map_err(|e| IrcError::Tls {
            detail: format!("Invalid PEM: {e}"),
        })? {
            rustls_pemfile::Item::X509Certificate(cert) => certs.push(cert),
            rustls_pemfile::Item::Pkcs1Key(k) => {
                *key = Some(rustls::pki_types::PrivateKeyDer::Pkcs1(k));
            }
            rustls_pemfile::Item::Pkcs8Key(k) => {
                *key = Some(rustls::pki_types::PrivateKeyDer::Pkcs8(k));
            }
            rustls_pemfile::Item::Sec1Key(k) => {
                *key = Some(rustls::pki_types::PrivateKeyDer::Sec1(k));
            }
            _ => {}
        }
    }
    Ok(())
}

#[derive(Debug)]
struct AcceptAny;

impl ServerCertVerifier for AcceptAny {
    fn verify_server_cert(
        &self,
        _end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
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
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        rustls::crypto::ring::default_provider()
            .signature_verification_algorithms
            .supported_schemes()
    }
}

pub struct TlsIrc {
    reader: BufReader<StreamOwned<ClientConnection, TcpStream>>,
    server_fingerprint: String,
}

impl TlsIrc {
    pub fn server_fingerprint(&self) -> &str {
        &self.server_fingerprint
    }

    pub fn close_notify(&mut self) -> Result<(), IrcError> {
        self.reader.get_mut().conn.send_close_notify();
        self.reader.get_mut().flush().map_err(|e| IrcError::Network {
            detail: e.to_string(),
        })?;
        let _ = self.reader.get_ref().sock.shutdown(Shutdown::Both);
        Ok(())
    }
}

impl IrcIo for TlsIrc {
    fn set_read_timeout(&mut self, timeout: Duration) -> Result<(), IrcError> {
        self.reader
            .get_ref()
            .sock
            .set_read_timeout(Some(timeout))
            .map_err(|e| IrcError::Network {
                detail: e.to_string(),
            })
    }

    fn send(&mut self, line: &str) -> Result<(), IrcError> {
        let stream = self.reader.get_mut();
        stream
            .write_all(line.as_bytes())
            .and_then(|_| stream.write_all(b"\r\n"))
            .and_then(|_| stream.flush())
            .map_err(|e| {
                if e.kind() == std::io::ErrorKind::TimedOut {
                    IrcError::Timeout {
                        detail: e.to_string(),
                    }
                } else {
                    IrcError::Network {
                        detail: e.to_string(),
                    }
                }
            })
    }

    fn recv(&mut self) -> Result<String, IrcError> {
        let mut line = String::new();
        let n = self.reader.read_line(&mut line).map_err(|e| {
            if e.kind() == std::io::ErrorKind::TimedOut || e.kind() == std::io::ErrorKind::WouldBlock
            {
                IrcError::Timeout {
                    detail: e.to_string(),
                }
            } else {
                IrcError::Network {
                    detail: e.to_string(),
                }
            }
        })?;
        if n == 0 {
            return Err(IrcError::Network {
                detail: "connection closed".into(),
            });
        }
        Ok(line)
    }
}

#[cfg(test)]
mod tests {
    use super::{
        certificate_fingerprint_sha256, fingerprint_sha256, fingerprints_equal, io_detail,
        ipv6_then_ipv4, parse_host_port, tls_server_name,
    };

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
    }

    #[test]
    fn ipv6_is_tried_first_ipv4_is_fallback() {
        use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
        let v4 = SocketAddr::new(IpAddr::V4(Ipv4Addr::LOCALHOST), 1);
        let v6 = SocketAddr::new(IpAddr::V6(Ipv6Addr::LOCALHOST), 1);
        assert_eq!(ipv6_then_ipv4([v4, v6]), vec![v6, v4]);
        assert_eq!(
            io_detail(&std::io::Error::from_raw_os_error(101)),
            "network unreachable (no route, often IPv6)"
        );
    }

    #[test]
    fn pem_fingerprint_is_sha256_of_der() {
        let ck = rcgen::generate_simple_self_signed(vec!["localhost".into()]).expect("cert");
        let expected = fingerprint_sha256(ck.cert.der());
        assert_eq!(certificate_fingerprint_sha256(&ck.cert.pem()), expected);
        assert!(expected.contains(':'));
        assert!(fingerprints_equal(&expected.to_lowercase(), &expected));
        assert!(fingerprints_equal("", "AA:BB"));
        assert!(!fingerprints_equal("00:11", "AA:BB"));
    }

    #[test]
    fn tls_sni_for_url_is_dns_name_not_ip() {
        let name = tls_server_name("bouncer.example.com").expect("dns");
        assert!(
            matches!(name, rustls::pki_types::ServerName::DnsName(_)),
            "{name:?}"
        );
        let ip = tls_server_name("127.0.0.1").expect("ip");
        assert!(
            matches!(ip, rustls::pki_types::ServerName::IpAddress(_)),
            "{ip:?}"
        );
    }

}
