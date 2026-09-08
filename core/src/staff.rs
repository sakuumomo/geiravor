//! Staff HTML. Groups staff | dev | dj. See `docs/spec/schedule-staff.md`.

use crate::error::ApiError;
use crate::html::strip_tags;
use crate::news::RoleColor;
use crate::parse::{DJ_IMAGE_BASE, check_bound};

pub const STAFF_URL: &str = "https://r-a-d.io/staff";
pub const BOARD_STAFF_LABEL: &str = "Staff";
pub const STAFF_GROUP_STAFF: &str = "Staff";
pub const STAFF_GROUP_DEV: &str = "Developers";
pub const STAFF_GROUP_DJ: &str = "DJs";

#[uniffi::export]
pub fn board_staff_label() -> String {
    BOARD_STAFF_LABEL.to_string()
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StaffCard {
    pub name: String,
    pub image: String,
    pub role: RoleColor,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StaffGroup {
    pub role: RoleColor,
    pub label: String,
    pub cards: Vec<StaffCard>,
}

pub fn parse_staff(html: &str) -> Result<Vec<StaffGroup>, ApiError> {
    check_bound(html.as_bytes())?;
    let staff = section(html, "id=\"staff\"", "id=\"dev\"");
    let dev = section(html, "id=\"dev\"", "id=\"dj\"");
    let dj = html.find("id=\"dj\"").map(|i| &html[i..]).unwrap_or("");
    Ok(vec![
        StaffGroup {
            role: RoleColor::Staff,
            label: STAFF_GROUP_STAFF.into(),
            cards: cards(staff, RoleColor::Staff),
        },
        StaffGroup {
            role: RoleColor::Dev,
            label: STAFF_GROUP_DEV.into(),
            cards: cards(dev, RoleColor::Dev),
        },
        StaffGroup {
            role: RoleColor::Dj,
            label: STAFF_GROUP_DJ.into(),
            cards: cards(dj, RoleColor::Dj),
        },
    ])
}

fn section<'a>(html: &'a str, start: &str, end: &str) -> &'a str {
    let Some(i) = html.find(start) else {
        return "";
    };
    let rest = &html[i..];
    match rest.find(end) {
        Some(j) if j > 0 => &rest[..j],
        _ => rest,
    }
}

fn cards(html: &str, role: RoleColor) -> Vec<StaffCard> {
    let mut out = Vec::new();
    let mut rest = html;
    while let Some(i) = rest.find("dj-card-name") {
        let before = &rest[..i];
        rest = &rest[i + 12..];
        let name = crate::html::between(rest, ">", "</div>")
            .map(strip_tags)
            .unwrap_or_default();
        if name.is_empty() {
            continue;
        }
        let image = before
            .rfind("/api/dj-image/")
            .map(|j| {
                let rest = &before[j + 14..];
                let file: String = rest
                    .chars()
                    .take_while(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '.')
                    .collect();
                format!("{DJ_IMAGE_BASE}{file}")
            })
            .unwrap_or_default();
        out.push(StaffCard { name, image, role });
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn three_groups_in_order() {
        let g = parse_staff(include_str!("../tests/fixtures/staff.html")).unwrap();
        assert_eq!(g.len(), 3);
        assert_eq!(g[0].label, "Staff");
        assert_eq!(g[1].label, "Developers");
        assert_eq!(g[2].label, "DJs");
        assert!(g[0].cards.iter().any(|c| c.name == "exci"));
        assert!(g[1].cards.iter().any(|c| c.name == "Vin"));
        assert!(g[2].cards.iter().any(|c| c.name == "Hanyuu-sama"));
        assert!(g[2].cards[0].image.contains("dj-image"));
    }
}
