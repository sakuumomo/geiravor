//! Marker-based HTML helpers. Not a general HTML client.

pub fn decode_entities(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(i) = rest.find('&') {
        out.push_str(&rest[..i]);
        rest = &rest[i..];
        if let Some(end) = rest.find(';') {
            let ent = &rest[..=end];
            let ch = match ent {
                "&amp;" => "&",
                "&lt;" => "<",
                "&gt;" => ">",
                "&quot;" => "\"",
                "&nbsp;" => " ",
                "&#39;" | "&#x27;" | "&apos;" => "'",
                "&#34;" | "&#x22;" => "\"",
                _ => {
                    out.push('&');
                    rest = &rest[1..];
                    continue;
                }
            };
            out.push_str(ch);
            rest = &rest[end + 1..];
        } else {
            out.push_str(rest);
            return out;
        }
    }
    out.push_str(rest);
    out
}

pub fn collapse_ws(s: &str) -> String {
    let mut out = String::new();
    let mut gap = false;
    for c in decode_entities(s).chars() {
        if c.is_whitespace() {
            if !out.is_empty() {
                gap = true;
            }
        } else {
            if gap {
                out.push(' ');
                gap = false;
            }
            out.push(c);
        }
    }
    out
}

pub fn strip_tags(s: &str) -> String {
    let mut out = String::new();
    let mut in_tag = false;
    for c in s.chars() {
        match c {
            '<' => in_tag = true,
            '>' => in_tag = false,
            _ if !in_tag => out.push(c),
            _ => {}
        }
    }
    collapse_ws(&out)
}

pub fn between<'a>(s: &'a str, start: &str, end: &str) -> Option<&'a str> {
    let i = s.find(start)?;
    let rest = &s[i + start.len()..];
    let j = rest.find(end)?;
    Some(&rest[..j])
}

pub fn after<'a>(s: &'a str, start: &str) -> Option<&'a str> {
    let i = s.find(start)?;
    Some(&s[i + start.len()..])
}

/// Path-segment percent-encode (RFC 3986 unreserved).
pub fn path_encode(s: &str) -> String {
    let mut out = String::new();
    for b in s.as_bytes() {
        match *b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(*b as char);
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

/// `name="gorilla.csrf.Token"` then `value="…"`. Works inside HTML comments.
pub fn extract_csrf(html: &str) -> Option<String> {
    crate::parse::check_bound(html.as_bytes()).ok()?;
    let rest = after(html, "name=\"gorilla.csrf.Token\"")?;
    let v = after(rest, "value=\"")?;
    let end = v.find('"')?;
    let token = &v[..end];
    if token.is_empty() {
        None
    } else {
        Some(token.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn csrf_from_comment() {
        let html = include_str!("../tests/fixtures/csrf_search.html");
        assert_eq!(extract_csrf(html).as_deref(), Some("TESTTOKEN123"));
    }

    #[test]
    fn entities_and_ws() {
        assert_eq!(collapse_ws("a&amp;b  &#39;  c"), "a&b ' c");
    }
}
