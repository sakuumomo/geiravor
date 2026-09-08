//! IRC line parse. Never log IDENTIFY / PASS / AUTHENTICATE payloads.

use super::HANYUU;

pub fn strip_colors(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '\x0f' | '\x02' | '\x1d' | '\x1f' | '\x16' => {}
            '\x03' => {
                for _ in 0..2 {
                    if chars.peek().is_some_and(|d| d.is_ascii_digit()) {
                        chars.next();
                    }
                }
                if chars.peek() == Some(&',') {
                    chars.next();
                    for _ in 0..2 {
                        if chars.peek().is_some_and(|d| d.is_ascii_digit()) {
                            chars.next();
                        }
                    }
                }
            }
            _ => out.push(c),
        }
    }
    out
}

pub fn numeric(line: &str) -> Option<&str> {
    let rest = line.strip_prefix(':')?;
    rest.split_whitespace().nth(1)
}

pub fn assigned_nick_001(line: &str) -> Option<String> {
    if numeric(line) != Some("001") {
        return None;
    }
    line.split_whitespace().nth(2).map(str::to_string)
}

pub fn is_nick_error(line: &str) -> bool {
    matches!(numeric(line), Some("432" | "433" | "436" | "437"))
}

pub fn is_hanyuu_notice(line: &str) -> bool {
    let lower = line.to_ascii_lowercase();
    lower.contains("notice") && lower.contains(&HANYUU.to_ascii_lowercase())
}

pub fn named_song(notice: &str) -> Option<String> {
    let t = strip_colors(notice);
    let start = t.find('\'')?;
    let rest = &t[start + 1..];
    let end = rest.find('\'')?;
    Some(rest[..end].to_string())
}

pub fn hanyuu_added(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("added") && t.contains("favorite")
}

pub fn hanyuu_already(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("already")
}

pub fn hanyuu_removed(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("removed")
}

pub fn hanyuu_unknown(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("unknown") || t.contains("not in") || t.contains("not-in")
}

pub fn np_key(np: &str) -> String {
    np.split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .to_lowercase()
}

pub fn np_match(a: &str, b: &str) -> bool {
    np_key(a) == np_key(b)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strips_color_then_names_song() {
        let line = ":Hanyuu-sama NOTICE x :\x033Added 'Foo - Bar' to your favorites.";
        assert_eq!(named_song(line).as_deref(), Some("Foo - Bar"));
        assert!(hanyuu_added(line));
    }
}
