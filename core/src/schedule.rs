use crate::html::strip_tags;

pub const SCHEDULE_URL: &str = "https://r-a-d.io/schedule";
pub const SCHEDULE_WEEKDAYS: [&str; 7] = [
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
];

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ScheduleDay {
    pub weekday: String,
    pub body: String,
    pub owner_name: String,
    pub owner_image: String,
}

/// Seven rows, Monday first. Duplicate desktop/mobile cells: first `data-weekday` wins.
pub fn parse_schedule(html: &str) -> Vec<ScheduleDay> {
    SCHEDULE_WEEKDAYS
        .iter()
        .map(|weekday| parse_day(html, weekday))
        .collect()
}

fn parse_day(html: &str, weekday: &str) -> ScheduleDay {
    let marker = format!("data-weekday=\"{weekday}\"");
    let Some(pos) = html.find(&marker) else {
        return empty_day(weekday);
    };
    let after_attr = &html[pos + marker.len()..];
    let Some(gt) = after_attr.find('>') else {
        return empty_day(weekday);
    };
    let rest = &after_attr[gt + 1..];
    let cut = rest.find("</div>").unwrap_or(rest.len());
    let body = strip_tags(&rest[..cut]);
    let window = cell_window(html, pos);
    ScheduleDay {
        weekday: weekday.to_string(),
        body,
        owner_name: owner_name(window, weekday),
        owner_image: dj_image(window),
    }
}

fn cell_window(html: &str, pos: usize) -> &str {
    let before = &html[..pos];
    let start = before
        .rfind("class=\"cell")
        .and_then(|i| before[..i].rfind('<'))
        .unwrap_or(0);
    let end = html[pos..]
        .find("class=\"cell")
        .map(|i| pos + i)
        .unwrap_or(html.len());
    &html[start..end]
}

fn empty_day(weekday: &str) -> ScheduleDay {
    ScheduleDay {
        weekday: weekday.to_string(),
        body: String::new(),
        owner_name: String::new(),
        owner_image: String::new(),
    }
}

fn owner_name(window: &str, weekday: &str) -> String {
    let mut from = 0usize;
    while let Some(rel) = window[from..].find("<h6") {
        let start = from + rel;
        let after = &window[start..];
        let Some(gt) = after.find('>') else {
            from = start + 1;
            continue;
        };
        let rest = &after[gt + 1..];
        let Some(end) = rest.find("</h6>") else {
            from = start + 1;
            continue;
        };
        let name = strip_tags(&rest[..end]);
        from = start + 1;
        if name.is_empty() || name.eq_ignore_ascii_case(weekday) {
            continue;
        }
        return name;
    }
    String::new()
}

fn dj_image(window: &str) -> String {
    let marker = "/api/dj-image/";
    let Some(pos) = window.find(marker) else {
        return String::new();
    };
    window[pos + marker.len()..]
        .chars()
        .take_while(|c| *c != '"' && *c != '\'' && *c != ' ' && *c != '?')
        .collect()
}
