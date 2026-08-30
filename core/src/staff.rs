use crate::html::strip_tags;

pub const STAFF_URL: &str = "https://r-a-d.io/staff";
pub const STAFF_ROLES: [&str; 3] = ["staff", "dev", "dj"];

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct StaffMember {
    pub name: String,
    pub image: String,
    pub role: String,
}

/// Cards in `#staff`, `#dev`, `#dj` order. Image is the dj-image filename.
pub fn parse_staff(html: &str) -> Vec<StaffMember> {
    let mut out = Vec::new();
    for (i, role) in STAFF_ROLES.iter().enumerate() {
        let next = STAFF_ROLES.get(i + 1).copied();
        let section = slice_id(html, role, next);
        out.extend(parse_cards(section, role));
    }
    out
}

fn slice_id<'a>(html: &'a str, id: &str, next: Option<&str>) -> &'a str {
    let marker = format!("id=\"{id}\"");
    let Some(start) = html.find(&marker) else {
        return "";
    };
    let after = start + marker.len();
    let end = next
        .and_then(|n| {
            html[after..]
                .find(&format!("id=\"{n}\""))
                .map(|rel| after + rel)
        })
        .unwrap_or(html.len());
    &html[start..end]
}

fn parse_cards(section: &str, role: &str) -> Vec<StaffMember> {
    let mut out = Vec::new();
    let mut from = 0usize;
    let marker = "dj-card-name";
    while let Some(rel) = section[from..].find(marker) {
        let start = from + rel;
        let after = &section[start..];
        let Some(gt) = after.find('>') else {
            from = start + 1;
            continue;
        };
        let rest = &after[gt + 1..];
        let Some(cut) = rest.find('<') else {
            from = start + 1;
            continue;
        };
        let name = strip_tags(&rest[..cut]);
        if name.is_empty() {
            from = start + 1;
            continue;
        }
        let image = last_dj_image(&section[..start]);
        out.push(StaffMember {
            name,
            image,
            role: role.to_string(),
        });
        from = start + 1;
    }
    out
}

fn last_dj_image(lookback: &str) -> String {
    let marker = "/api/dj-image/";
    let Some(pos) = lookback.rfind(marker) else {
        return String::new();
    };
    lookback[pos + marker.len()..]
        .chars()
        .take_while(|c| *c != '"' && *c != '\'' && *c != ' ' && *c != '?')
        .collect()
}
