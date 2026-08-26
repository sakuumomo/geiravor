use std::time::Duration;

use crate::USER_AGENT;
use crate::status::ParseError;

const CONNECT_TIMEOUT: Duration = Duration::from_secs(5);
const REQUEST_TIMEOUT: Duration = Duration::from_secs(10);

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum ApiError {
    Network { detail: String },
    Http { status: u16, detail: String },
    Decode { detail: String },
}

impl std::fmt::Display for ApiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Network { detail } => write!(f, "network: {detail}"),
            Self::Http { status, detail } => write!(f, "http {status}: {detail}"),
            Self::Decode { detail } => write!(f, "decode: {detail}"),
        }
    }
}

impl std::error::Error for ApiError {}

impl From<ParseError> for ApiError {
    fn from(err: ParseError) -> Self {
        Self::Decode { detail: err.0 }
    }
}

impl ApiError {
    pub fn from_reqwest(err: reqwest::Error) -> Self {
        if let Some(status) = err.status() {
            Self::Http {
                status: status.as_u16(),
                detail: err.to_string(),
            }
        } else if err.is_decode() {
            Self::Decode {
                detail: err.to_string(),
            }
        } else {
            Self::Network {
                detail: err.to_string(),
            }
        }
    }
}

pub fn blocking_client() -> Result<reqwest::blocking::Client, ApiError> {
    reqwest::blocking::Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(CONNECT_TIMEOUT)
        .timeout(REQUEST_TIMEOUT)
        .cookie_store(true)
        .build()
        .map_err(ApiError::from_reqwest)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::thread;

    fn spawn_cookie_server() -> String {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind");
        let addr = listener.local_addr().expect("addr");
        thread::spawn(move || {
            for incoming in listener.incoming().take(2) {
                let mut stream = incoming.expect("accept");
                let mut buf = [0u8; 4096];
                let n = stream.read(&mut buf).unwrap_or(0);
                let req = String::from_utf8_lossy(&buf[..n]);
                let (extra_headers, body) = if req.starts_with("GET /set") {
                    (
                        "Set-Cookie: _gorilla_csrf=abc; Path=/\r\n",
                        "ok".to_string(),
                    )
                } else {
                    let cookie = req
                        .lines()
                        .find(|line| line.to_ascii_lowercase().starts_with("cookie:"))
                        .unwrap_or("");
                    ("", cookie.to_string())
                };
                let resp = format!(
                    "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n{extra_headers}\r\n{body}",
                    body.len()
                );
                let _ = stream.write_all(resp.as_bytes());
            }
        });
        format!("http://{addr}")
    }

    #[test]
    fn parse_error_is_decode() {
        let err = ApiError::from(ParseError("bad json".into()));
        assert!(matches!(err, ApiError::Decode { detail } if detail == "bad json"));
    }

    #[test]
    fn cookie_jar_sends_set_cookie_on_next_get() {
        let base = spawn_cookie_server();
        let client = blocking_client().expect("client");
        client
            .get(format!("{base}/set"))
            .send()
            .expect("set")
            .error_for_status()
            .expect("set ok");
        let echoed = client
            .get(format!("{base}/echo"))
            .send()
            .expect("echo")
            .text()
            .expect("echo body");
        assert!(
            echoed.contains("_gorilla_csrf=abc"),
            "cookie jar missed csrf cookie: {echoed:?}"
        );
    }
}
