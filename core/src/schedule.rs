//! Schedule HTML. Monday first. See `docs/spec/schedule-staff.md`.

use crate::error::ApiError;
use crate::html::{between, strip_tags};
use crate::parse::{DJ_IMAGE_BASE, check_bound};

pub const SCHEDULE_URL: &str = "https://r-a-d.io/schedule";
pub const EST_ZONE: &str = "America/New_York";
pub const BOARD_SCHEDULE_LABEL: &str = "Schedule";

#[uniffi::export]
pub fn board_schedule_label() -> String {
    BOARD_SCHEDULE_LABEL.to_string()
}

pub const WEEKDAYS: [&str; 7] = [
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
];

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ScheduleDay {
    pub weekday: String,
    pub body: String,
    pub owner: String,
    pub image: String,
}

#[uniffi::export]
pub fn est_zone_id() -> String {
    EST_ZONE.to_string()
}

pub fn parse_schedule(html: &str) -> Result<Vec<ScheduleDay>, ApiError> {
    check_bound(html.as_bytes())?;
    let mut days = Vec::with_capacity(7);
    for day in WEEKDAYS {
        let marker = format!("data-weekday=\"{day}\"");
        let Some(at) = html.find(&marker) else {
            days.push(ScheduleDay {
                weekday: day.to_string(),
                body: String::new(),
                owner: String::new(),
                image: String::new(),
            });
            continue;
        };
        let start = html[..at]
            .rfind("class=\"cell")
            .and_then(|i| html[..i].rfind("<div"))
            .unwrap_or_else(|| html[..at].rfind("<div").unwrap_or(at));
        // One `.cell` only (desktop copy). Next cell is the mobile duplicate or
        // the next day — never walk to the next weekday marker (that stole
        // Saturday's image, which sits *before* Saturday's marker).
        let after_open = html[start..]
            .find('>')
            .map(|i| start + i + 1)
            .unwrap_or(start + 1);
        let end = html[after_open..]
            .find("class=\"cell")
            .map(|i| {
                html[..after_open + i]
                    .rfind("<div")
                    .unwrap_or(after_open + i)
            })
            .unwrap_or(html.len());
        let window = &html[start..end];
        let body = between(window, &marker, "</div>")
            .map(|s| strip_tags(&s[s.find('>').map(|x| x + 1).unwrap_or(0)..]))
            .unwrap_or_default();
        let mut owner = String::new();
        let mut search = window;
        while let Some(h) = search.find("<h6") {
            search = &search[h + 3..];
            if let Some(inner) = between(search, ">", "</h6>") {
                let name = strip_tags(inner);
                if !name.is_empty() && WEEKDAYS.iter().all(|d| *d != name) {
                    owner = name;
                    break;
                }
            }
        }
        let image = window
            .find("/api/dj-image/")
            .map(|j| {
                let rest = &window[j + 14..];
                let file: String = rest
                    .chars()
                    .take_while(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '.')
                    .collect();
                format!("{DJ_IMAGE_BASE}{file}")
            })
            .unwrap_or_default();
        days.push(ScheduleDay {
            weekday: day.to_string(),
            body,
            owner,
            image,
        });
    }
    Ok(days)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn monday_first_one_copy() {
        let days = parse_schedule(include_str!("../tests/fixtures/schedule.html")).unwrap();
        assert_eq!(days.len(), 7);
        assert_eq!(days[0].weekday, "Monday");
        assert_eq!(days[6].weekday, "Sunday");
        assert_eq!(days[0].owner, "kipukun");
        assert!(days[0].image.contains("45-"));
        assert!(days[0].body.contains("8pm"));
        assert!(days[1].image.contains("26-"));
        assert_eq!(days[1].owner, "Master_Bacon");
        assert!(days[3].image.contains("49-"));
        assert!(days[4].owner.is_empty());
        assert!(days[4].image.is_empty());
        assert!(!days[4].body.is_empty());
        assert!(days[5].image.contains("73-"));
        assert_eq!(days[5].owner, "HIACE");
        assert!(days[6].image.contains("26-"));
    }
}
