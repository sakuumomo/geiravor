use crate::http::ApiError;

pub fn post_with_csrf(
    client: &reqwest::blocking::Client,
    url: &str,
    token: &str,
) -> Result<String, ApiError> {
    post_form_csrf(client, url, token, &[])
}

pub fn post_form_csrf(
    client: &reqwest::blocking::Client,
    url: &str,
    token: &str,
    fields: &[(&str, &str)],
) -> Result<String, ApiError> {
    let mut form: Vec<(&str, &str)> = Vec::with_capacity(fields.len() + 1);
    form.push(("gorilla.csrf.Token", token));
    form.extend_from_slice(fields);
    let response = client
        .post(url)
        .header(CSRF_HEADER, token)
        .form(&form)
        .send()
        .and_then(|r| r.error_for_status())
        .map_err(ApiError::from_reqwest)?;
    response.text().map_err(ApiError::from_reqwest)
}

/// HTML page that embeds `gorilla.csrf.Token`. Not `/v1/search` (navbar HTML).
pub const CSRF_BOOTSTRAP_URL: &str = "https://r-a-d.io/search";
pub const CSRF_HEADER: &str = "X-CSRF-Token";
pub const CSRF_COOKIE: &str = "_gorilla_csrf";

const FIELD_NAME: &str = "name=\"gorilla.csrf.Token\"";
const VALUE_PREFIX: &str = "value=\"";

/// Pull the gorilla hidden-input token. Not a general HTML parser: one field name + value.
pub fn extract_csrf_token(html: &str) -> Result<String, ApiError> {
    let field = html.find(FIELD_NAME).ok_or_else(|| ApiError::Decode {
        detail: "missing gorilla.csrf.Token".into(),
    })?;
    let after_field = &html[field + FIELD_NAME.len()..];
    let value_at = after_field
        .find(VALUE_PREFIX)
        .ok_or_else(|| ApiError::Decode {
            detail: "gorilla.csrf.Token missing value".into(),
        })?;
    let rest = &after_field[value_at + VALUE_PREFIX.len()..];
    let end = rest.find('"').ok_or_else(|| ApiError::Decode {
        detail: "gorilla.csrf.Token unterminated value".into(),
    })?;
    let token = rest[..end].trim();
    if token.is_empty() {
        return Err(ApiError::Decode {
            detail: "gorilla.csrf.Token empty".into(),
        });
    }
    Ok(token.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::http::blocking_client;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::sync::{Arc, Mutex};
    use std::thread;

    fn read_http(stream: &mut impl Read) -> String {
        let mut buf = Vec::new();
        let mut tmp = [0u8; 1024];
        loop {
            let n = stream.read(&mut tmp).unwrap_or(0);
            if n == 0 {
                break;
            }
            buf.extend_from_slice(&tmp[..n]);
            if buf.windows(4).any(|w| w == b"\r\n\r\n") {
                break;
            }
        }
        String::from_utf8_lossy(&buf).into_owned()
    }

    #[test]
    fn extracts_token_from_commented_search_form() {
        let html = include_str!("../tests/fixtures/csrf_token_comment.html");
        assert_eq!(extract_csrf_token(html).unwrap(), "test-token-from-comment");
    }

    #[test]
    fn extracts_token_from_request_button() {
        let html = include_str!("../tests/fixtures/csrf_token_input.html");
        assert_eq!(extract_csrf_token(html).unwrap(), "test-token-from-input");
    }

    #[test]
    fn missing_field_is_decode() {
        assert!(matches!(
            extract_csrf_token("<html></html>"),
            Err(ApiError::Decode { .. })
        ));
    }

    #[test]
    fn bootstrap_get_then_post_sends_cookie_and_header() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let addr = listener.local_addr().unwrap();
        let seen = Arc::new(Mutex::new(String::new()));
        let seen_post = seen.clone();
        thread::spawn(move || {
            for (i, incoming) in listener.incoming().take(2).enumerate() {
                let mut stream = incoming.unwrap();
                let req = read_http(&mut stream);
                let body = if i == 0 {
                    include_str!("../tests/fixtures/csrf_token_comment.html")
                } else {
                    *seen_post.lock().unwrap() = req.clone();
                    "{\"success\":\"ok\"}"
                };
                let set_cookie = if i == 0 {
                    "Set-Cookie: _gorilla_csrf=jar-cookie; Path=/\r\n"
                } else {
                    ""
                };
                let resp = format!(
                    "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n{set_cookie}\r\n{body}",
                    body.len()
                );
                let _ = stream.write_all(resp.as_bytes());
            }
        });
        let base = format!("http://{addr}");
        let client = blocking_client().unwrap();
        let html = client
            .get(format!("{base}/search"))
            .send()
            .unwrap()
            .text()
            .unwrap();
        let token = extract_csrf_token(&html).unwrap();
        assert_eq!(token, "test-token-from-comment");
        post_with_csrf(&client, &format!("{base}/request/42"), &token).unwrap();
        let posted = seen.lock().unwrap().clone();
        assert!(
            posted.contains("X-CSRF-Token: test-token-from-comment")
                || posted.contains("x-csrf-token: test-token-from-comment"),
            "missing CSRF header: {posted}"
        );
        assert!(
            posted.to_ascii_lowercase().contains("cookie:")
                && posted.contains("_gorilla_csrf=jar-cookie"),
            "cookie jar did not send csrf cookie: {posted}"
        );
    }
}
