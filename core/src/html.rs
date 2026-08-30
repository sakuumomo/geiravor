/// Theme directory in `href="/assets/{name}/css/…"`. First stylesheet wins.
#[uniffi::export]
pub fn parse_theme_name(html: &str) -> Option<String> {
    let marker = "href=\"/assets/";
    let mut from = 0usize;
    while let Some(rel) = html[from..].find(marker) {
        let start = from + rel + marker.len();
        let rest = &html[start..];
        let name: String = rest
            .chars()
            .take_while(|c| *c != '/' && *c != '"')
            .collect();
        if !name.is_empty() && rest[name.len()..].starts_with("/css/") {
            return Some(name);
        }
        from = start + 1;
    }
    None
}

pub fn decode_basic(input: &str) -> String {
    input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

pub fn strip_tags(input: &str) -> String {
    let mut out = String::new();
    let mut in_tag = false;
    for c in input.chars() {
        match c {
            '<' => in_tag = true,
            '>' => in_tag = false,
            _ if !in_tag => out.push(c),
            _ => {}
        }
    }
    decode_basic(&out)
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}
