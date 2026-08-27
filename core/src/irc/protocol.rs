pub fn strip_irc(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut chars = input.chars().peekable();
    while let Some(c) = chars.next() {
        match c as u32 {
            0x02 | 0x0f | 0x16 | 0x1d | 0x1f | 0x1e => {}
            0x03 => {
                eat(&mut chars, 2, |ch| ch.is_ascii_digit());
                if chars.peek() == Some(&',') {
                    chars.next();
                    eat(&mut chars, 2, |ch| ch.is_ascii_digit());
                }
            }
            0x04 => eat(&mut chars, 6, |ch| ch.is_ascii_hexdigit()),
            _ => out.push(c),
        }
    }
    out
}

fn eat(chars: &mut std::iter::Peekable<std::str::Chars<'_>>, max: usize, ok: impl Fn(char) -> bool) {
    for _ in 0..max {
        match chars.peek() {
            Some(&ch) if ok(ch) => {
                chars.next();
            }
            _ => break,
        }
    }
}

pub fn normalize_np(np: &str) -> String {
    strip_irc(np)
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .to_lowercase()
}

pub fn np_match(intended: &str, named: &str) -> bool {
    !intended.trim().is_empty() && normalize_np(intended) == normalize_np(named)
}

pub fn nicks_match(left: &str, right: &str) -> bool {
    let a = left.trim();
    let b = right.trim();
    !a.is_empty() && fold_nick(a) == fold_nick(b)
}

/// RFC 1459 casemap (`[]\\~` ≡ `{}|^`) plus ASCII case. Rizon uses this.
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

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IrcLine {
    Ping(String),
    Welcome { nick: String },
    NickInUse,
    NickChange { from: String, to: String },
    Notice { from: String, text: String },
    Privmsg { from: String, text: String },
    CapLs { continued: bool, list: String },
    CapAck,
    CapNak,
    Authenticate(String),
    SaslSuccess,
    SaslFail,
    Other,
}

pub fn parse_line(raw: &str) -> IrcLine {
    let line = raw.trim_end_matches(['\r', '\n']);
    let (prefix, rest) = if let Some(stripped) = line.strip_prefix(':') {
        match stripped.split_once(' ') {
            Some((p, r)) => (Some(p), r),
            None => (Some(stripped), ""),
        }
    } else {
        (None, line)
    };
    let (command, params) = match rest.split_once(' ') {
        Some((c, p)) => (c, p),
        None => (rest, ""),
    };
    let trailing = if let Some(t) = params.strip_prefix(':') {
        t
    } else if let Some((_, t)) = params.split_once(" :") {
        t
    } else {
        params
    }
    .trim();
    let first = first_param(params);
    let from = prefix
        .and_then(|p| p.split(['!', '@']).next())
        .unwrap_or("")
        .to_string();
    match command.to_ascii_uppercase().as_str() {
        "PING" => IrcLine::Ping(trailing.to_string()),
        "001" => IrcLine::Welcome { nick: first },
        "432" | "433" | "436" | "437" => IrcLine::NickInUse,
        "NICK" => IrcLine::NickChange {
            from,
            to: trailing.to_string(),
        },
        "NOTICE" => IrcLine::Notice {
            from,
            text: trailing.to_string(),
        },
        "PRIVMSG" => IrcLine::Privmsg {
            from,
            text: trailing.to_string(),
        },
        "CAP" => parse_cap(params, trailing),
        "AUTHENTICATE" => IrcLine::Authenticate(trailing.to_string()),
        "900" | "903" => IrcLine::SaslSuccess,
        "902" | "904" | "905" | "906" | "907" => IrcLine::SaslFail,
        _ => IrcLine::Other,
    }
}

fn parse_cap(params: &str, trailing: &str) -> IrcLine {
    let upper = params.to_ascii_uppercase();
    if upper.contains(" NAK") {
        IrcLine::CapNak
    } else if upper.contains(" ACK") {
        IrcLine::CapAck
    } else if upper.contains(" LS") {
        IrcLine::CapLs {
            continued: upper.contains(" LS *"),
            list: trailing.to_string(),
        }
    } else {
        IrcLine::Other
    }
}

fn first_param(params: &str) -> String {
    let middle = params.split_once(" :").map(|(m, _)| m).unwrap_or(params);
    middle
        .trim()
        .strip_prefix(':')
        .unwrap_or(middle)
        .split_whitespace()
        .next()
        .unwrap_or("")
        .to_string()
}

pub fn parse_hanyuu(text: &str) -> HanyuuReply {
    let t = strip_irc(text);
    if let Some(name) = between(&t, "Added '", "' to your favorites.") {
        return HanyuuReply::Added(name);
    }
    if let Some(name) = between(&t, "You already have '", "' favorited.") {
        return HanyuuReply::Already(name);
    }
    if t.contains("I don't know of a song with that ID") {
        return HanyuuReply::UnknownId;
    }
    if t.to_ascii_lowercase().contains("no such song") {
        return HanyuuReply::UnknownId;
    }
    if let Some(name) = between(&t, "'", "' is removed from your favorites.") {
        return HanyuuReply::Removed(name);
    }
    if let Some(name) = between(&t, "You don't have '", "' in your favorites.") {
        return HanyuuReply::NotFavorited(name);
    }
    HanyuuReply::Other(t)
}

fn between(hay: &str, start: &str, end: &str) -> Option<String> {
    let rest = hay.split_once(start)?.1;
    let name = rest.split_once(end)?.0;
    Some(name.to_string())
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HanyuuReply {
    Added(String),
    Already(String),
    Removed(String),
    NotFavorited(String),
    UnknownId,
    Other(String),
}

impl HanyuuReply {
    pub fn named(&self) -> Option<&str> {
        match self {
            Self::Added(n) | Self::Already(n) | Self::Removed(n) | Self::NotFavorited(n) => {
                Some(n.as_str())
            }
            Self::UnknownId | Self::Other(_) => None,
        }
    }

    pub fn is_added(&self) -> bool {
        matches!(self, Self::Added(_))
    }

    pub fn matches_intended(&self, intended: &str) -> bool {
        self.named().is_some_and(|n| np_match(intended, n))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strips_valkyrie_color_tokens_and_irc_codes() {
        assert_eq!(
            strip_irc("Added \x033'Aimer - ninelie'\x03 to your favorites."),
            "Added 'Aimer - ninelie' to your favorites."
        );
    }

    #[test]
    fn parse_added_already_unknown() {
        assert_eq!(
            parse_hanyuu("Added 'Hirasawa Susumu - Gats' to your favorites."),
            HanyuuReply::Added("Hirasawa Susumu - Gats".into())
        );
        assert_eq!(
            parse_hanyuu("You already have 'Hirasawa Susumu - Gats' favorited."),
            HanyuuReply::Already("Hirasawa Susumu - Gats".into())
        );
        assert_eq!(
            parse_hanyuu("I don't know of a song with that ID..."),
            HanyuuReply::UnknownId
        );
        assert_eq!(
            parse_hanyuu("'Hirasawa Susumu - Gats' is removed from your favorites."),
            HanyuuReply::Removed("Hirasawa Susumu - Gats".into())
        );
        assert_eq!(
            parse_hanyuu("You don't have 'Hirasawa Susumu - Gats' in your favorites."),
            HanyuuReply::NotFavorited("Hirasawa Susumu - Gats".into())
        );
    }

    #[test]
    fn np_match_is_case_and_space_insensitive() {
        assert!(np_match("Hirasawa Susumu - Gats", "hirasawa  susumu - gats"));
        assert!(!np_match("A - B", "C - D"));
    }

    #[test]
    fn welcome_carries_assigned_nick() {
        assert_eq!(
            parse_line(":irc.rizon.net 001 Geiravor :Welcome to the Internet Relay Network"),
            IrcLine::Welcome {
                nick: "Geiravor".into()
            },
        );
        assert_eq!(
            parse_line(":irc 001 Geiravor_ :welcome"),
            IrcLine::Welcome {
                nick: "Geiravor_".into()
            },
        );
    }

    #[test]
    fn nick_change_parses_from_and_to() {
        assert_eq!(
            parse_line(":Geiravor!u@h NICK :Geiravor_"),
            IrcLine::NickChange {
                from: "Geiravor".into(),
                to: "Geiravor_".into()
            },
        );
        assert!(nicks_match("Geiravor", "geiravor"));
        assert!(nicks_match("[geo]", "{geo}"));
        assert!(!nicks_match("Geiravor", "Geiravor_"));
        assert_eq!(
            parse_line(":irc 437 * Geiravor :Nick is unavailable."),
            IrcLine::NickInUse,
        );
        assert_eq!(
            parse_line(":irc 432 * bad nick :Erroneous Nickname"),
            IrcLine::NickInUse,
        );
        assert_eq!(
            parse_line(":irc CAP * LS :sasl multi-prefix"),
            IrcLine::CapLs {
                continued: false,
                list: "sasl multi-prefix".into()
            },
        );
        assert_eq!(
            parse_line(":irc CAP * ACK :sasl"),
            IrcLine::CapAck,
        );
        assert_eq!(parse_line("AUTHENTICATE +"), IrcLine::Authenticate("+".into()));
        assert_eq!(parse_line(":irc 903 Geiravor :ok"), IrcLine::SaslSuccess);
        assert_eq!(parse_line(":irc 904 Geiravor :fail"), IrcLine::SaslFail);
    }
}
