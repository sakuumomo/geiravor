use geiravor_core::{STAFF_URL, parse_staff, parse_theme_name};

#[test]
fn staff_url_is_html() {
    assert_eq!(STAFF_URL, "https://r-a-d.io/staff");
}

#[test]
fn parse_staff_groups_staff_dev_dj() {
    let members = parse_staff(include_str!("fixtures/staff.html"));
    assert_eq!(
        members
            .iter()
            .map(|m| (m.role.as_str(), m.name.as_str(), m.image.as_str()))
            .collect::<Vec<_>>(),
        vec![
            ("staff", "exci", "41-cce8bf9997de6186.png"),
            ("staff", "jii-san", "58-123cadc3310a2d28.png"),
            ("dev", "Vin", "29-17f60072.png"),
            ("dj", "Hanyuu-sama", "18-e0177611a37081b5.png"),
            ("dj", "kipukun", "45-a90ca4f16828f026.png"),
        ]
    );
}

#[test]
fn parse_staff_skips_empty_groups_without_inventing_hanyuu() {
    let html = r#"<div id="staff"></div><div id="dev"></div><div id="dj"></div>"#;
    assert!(parse_staff(html).is_empty());
}

#[test]
fn theme_name_from_staff_stylesheet() {
    assert_eq!(
        parse_theme_name(include_str!("fixtures/staff.html")).as_deref(),
        Some("default-dark")
    );
    assert_eq!(
        parse_theme_name(r#"<link rel="stylesheet" href="/assets/christmas/css/bulma.min.css" />"#)
            .as_deref(),
        Some("christmas")
    );
    assert_eq!(parse_theme_name(r#"<script src="/assets/js/radio.js">"#), None);
}
