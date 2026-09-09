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

/// RFC 1459 casemap (`[]\\~` ≡ `{|^`) plus ASCII case. Rizon uses this.
pub fn nicks_match(left: &str, right: &str) -> bool {
    let a = left.trim();
    let b = right.trim();
    !a.is_empty() && fold_nick(a) == fold_nick(b)
}

fn fold_nick(nick: &str) -> String {
    nick.chars()
        .map(|c| match c {
            'A'..='Z' => c.to_ascii_lowercase(),
            '[' => '{',
            ']' => '}',
            '\\' => '|',
            '~' => '^',
            other => other,
        })
        .collect()
}

/// `CAP LS` list and whether more lines follow (`LS *`).
pub fn cap_ls(line: &str) -> Option<(bool, String)> {
    let mut parts = line.split_whitespace();
    if line.starts_with(':') {
        parts.next()?;
    }
    if !parts.next()?.eq_ignore_ascii_case("CAP") {
        return None;
    }
    parts.next()?;
    if !parts.next()?.eq_ignore_ascii_case("LS") {
        return None;
    }
    let rest: Vec<&str> = parts.collect();
    if rest.is_empty() {
        return Some((false, String::new()));
    }
    let continued = rest[0] == "*";
    let list = rest
        .iter()
        .skip(usize::from(continued))
        .map(|s| s.trim_start_matches(':'))
        .collect::<Vec<_>>()
        .join(" ");
    Some((continued, list))
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

pub fn is_authenticate_plus(line: &str) -> bool {
    let t = line.trim();
    let upper = t.to_ascii_uppercase();
    upper == "AUTHENTICATE +" || upper.ends_with(" AUTHENTICATE +")
}

pub fn is_cap_ack(line: &str) -> bool {
    let mut parts = line.split_whitespace();
    if line.starts_with(':') {
        parts.next();
    }
    parts.next().is_some_and(|c| c.eq_ignore_ascii_case("CAP"))
        && parts.nth(1).is_some_and(|c| c.eq_ignore_ascii_case("ACK"))
}

pub fn is_hanyuu_notice(line: &str) -> bool {
    let lower = line.to_ascii_lowercase();
    let h = HANYUU.to_ascii_lowercase();
    lower.contains(&h)
        && (lower.contains(" notice ")
            || lower.contains(" privmsg ")
            || lower.contains("\tnotice "))
}

pub fn named_song(notice: &str) -> Option<String> {
    let t = strip_colors(notice);
    let start = t.find('\'')?;
    let end = t.rfind('\'')?;
    if end <= start {
        return None;
    }
    Some(t[start + 1..end].to_string())
}

pub fn hanyuu_added(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("added") && t.contains("favorite")
}

pub fn hanyuu_already(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("already") && t.contains("favorite") && !t.contains("added")
}

pub fn hanyuu_removed(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("removed")
}

pub fn hanyuu_not_favorited(notice: &str) -> bool {
    let t = strip_colors(notice).to_ascii_lowercase();
    t.contains("don't have") || t.contains("do not have")
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
        assert_eq!(
            named_song("Added 'Don't Say Lazy' to your favorites.").as_deref(),
            Some("Don't Say Lazy")
        );
        assert!(hanyuu_already("That's already in your favorites."));
        assert!(!hanyuu_already("Added 'Already Gone' to your favorites."));
    }

    #[test]
    fn nicks_match_rfc1459() {
        assert!(nicks_match("Geiravor", "geiravor"));
        assert!(nicks_match("nick[a]", "nick{a}"));
        assert!(!nicks_match("alice", "bob"));
        assert!(!nicks_match("", "x"));
    }

    #[test]
    fn cap_ls_finds_sasl_and_continuation() {
        let (cont, list) = cap_ls(":irc CAP * LS :multi-prefix sasl=PLAIN,EXTERNAL").unwrap();
        assert!(!cont);
        assert!(list_has_sasl(&list));
        let (cont, list) = cap_ls(":irc CAP * LS * :sasl").unwrap();
        assert!(cont);
        assert!(list_has_sasl(&list));
        assert!(!list_has_sasl("multi-prefix account-tag"));
    }
}
