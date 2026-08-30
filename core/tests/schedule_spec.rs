use geiravor_core::{SCHEDULE_URL, parse_schedule, parse_theme_name};

#[test]
fn schedule_url_is_html() {
    assert_eq!(SCHEDULE_URL, "https://r-a-d.io/schedule");
}

#[test]
fn parse_schedule_is_monday_first_seven_rows() {
    let days = parse_schedule(include_str!("fixtures/schedule.html"));
    assert_eq!(days.len(), 7);
    assert_eq!(
        days.iter()
            .map(|d| d.weekday.as_str())
            .collect::<Vec<_>>(),
        vec![
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        ]
    );
    assert_eq!(days[0].owner_name, "kipukun");
    assert_eq!(days[0].owner_image, "45-a90ca4f16828f026.png");
    assert_eq!(days[0].body, "streams at 8pm");
    assert_eq!(days[1].owner_name, "Master_Bacon");
    assert!(days[1].body.contains("11:59 pm EST"));
    assert!(days[1].body.contains("Tuesday's gone"));
    assert_eq!(days[2].body, "Should be around 22:00.");
    assert_eq!(days[4].owner_name, "");
    assert_eq!(days[4].owner_image, "");
    assert_eq!(days[4].body, "Usually starts around 8PM.");
    assert_eq!(days[6].owner_name, "");
    assert_eq!(days[6].body, "");
}

#[test]
fn empty_html_is_seven_empty_days_not_hanyuu() {
    let days = parse_schedule("");
    assert_eq!(days.len(), 7);
    assert!(days.iter().all(|d| d.owner_name.is_empty() && d.body.is_empty()));
}

#[test]
fn theme_name_from_schedule_stylesheet() {
    assert_eq!(
        parse_theme_name(include_str!("fixtures/schedule.html")).as_deref(),
        Some("default-dark")
    );
}
