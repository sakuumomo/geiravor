use serde::Deserialize;

use crate::http::ApiError;

pub const NEWS_URL: &str = "https://r-a-d.io/api/news";
pub const NEWS_LIST_URL: &str = "https://r-a-d.io/news";
pub const NEWS_PER_PAGE: i32 = 20;
pub const COMMENT_MAX: i32 = 500;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewsAuthor {
    pub id: i64,
    pub user: String,
    pub role: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewsArticle {
    pub id: i64,
    pub title: String,
    pub header: String,
    pub text: String,
    pub updated_at: String,
    pub author: NewsAuthor,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewsComment {
    pub id: i64,
    pub author: String,
    pub posted_at: String,
    pub body: String,
    pub role: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewsPage {
    pub current_page: i32,
    pub last_page: i32,
    pub data: Vec<NewsArticle>,
}

impl NewsPage {
    pub fn empty() -> Self {
        Self {
            current_page: 1,
            last_page: 1,
            data: Vec::new(),
        }
    }
}

#[derive(Deserialize)]
struct RawAuthor {
    id: i64,
    user: String,
}

#[derive(Deserialize)]
struct RawArticle {
    title: String,
    #[serde(default)]
    header: String,
    #[serde(default)]
    text: String,
    #[serde(default)]
    updated_at: String,
    author: RawAuthor,
}

pub fn news_entry_url(id: i64) -> String {
    format!("{NEWS_LIST_URL}/{id}")
}

pub fn news_list_url(page: i32) -> String {
    let page = page.max(1);
    if page <= 1 {
        NEWS_LIST_URL.to_string()
    } else {
        format!("{NEWS_LIST_URL}?page={page}")
    }
}

pub fn parse_news(json: &str) -> Result<Vec<NewsArticle>, ApiError> {
    let raw: Vec<RawArticle> = serde_json::from_str(json).map_err(|e| ApiError::Decode {
        detail: e.to_string(),
    })?;
    Ok(raw
        .into_iter()
        .map(|article| NewsArticle {
            id: 0,
            title: article.title,
            header: article.header,
            text: article.text,
            updated_at: article.updated_at,
            author: NewsAuthor {
                id: article.author.id,
                user: article.author.user,
                role: String::new(),
            },
        })
        .collect())
}

fn decode_basic(input: &str) -> String {
    input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

fn strip_tags(input: &str) -> String {
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

/// `href="/news/{id}"` then `news-title` inner text. Not a general HTML parser.
pub fn parse_news_ids(html: &str) -> Vec<(i64, String)> {
    parse_news_list_items(html)
        .into_iter()
        .map(|a| (a.id, a.title))
        .collect()
}

pub fn parse_news_last_page(html: &str) -> i32 {
    let mut last = 1i32;
    let mut from = 0usize;
    let marker = "/news?page=";
    while let Some(rel) = html[from..].find(marker) {
        let start = from + rel + marker.len();
        let rest = &html[start..];
        let digits: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
        if let Ok(n) = digits.parse::<i32>() {
            if n > 0 {
                last = last.max(n);
            }
        }
        from = start + 1;
    }
    last
}

pub fn parse_news_list(html: &str, page: i32) -> NewsPage {
    let data = parse_news_list_items(html);
    let page = page.max(1);
    let mut last = parse_news_last_page(html).max(1);
    if (data.len() as i32) < NEWS_PER_PAGE {
        last = last.max(page);
    }
    NewsPage {
        current_page: page,
        last_page: last.max(page),
        data,
    }
}

fn parse_news_list_items(html: &str) -> Vec<NewsArticle> {
    let mut out = Vec::new();
    let mut from = 0usize;
    let marker = "href=\"/news/";
    while let Some(rel) = html[from..].find(marker) {
        let start = from + rel + marker.len();
        let rest = &html[start..];
        let digits: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
        let Ok(id) = digits.parse::<i64>() else {
            from = start.max(from + 1);
            continue;
        };
        if id <= 0 {
            from = start + 1;
            continue;
        }
        let after = &rest[digits.len()..];
        let window_len = after.len().min(2800);
        let window = &after[..window_len];
        let title = inner_text(window, "news-title", "</span>")
            .map(strip_tags)
            .unwrap_or_default();
        if title.is_empty() {
            from = start + 1;
            continue;
        }
        let author = if let Some(block) = window.split("page-home-news-author").nth(1) {
            let gt = block.find('>').map(|i| i + 1).unwrap_or(0);
            let rest = &block[gt..];
            let cut = rest.find("<time").unwrap_or(rest.len());
            strip_tags(&rest[..cut])
        } else {
            String::new()
        };
        let updated_at = date_from_window(window);
        let header = if let Some(body) = window.split("message-body").nth(1) {
            let gt = body.find('>').map(|i| i + 1).unwrap_or(0);
            let rest = &body[gt..];
            let cut = rest.find("</div>").unwrap_or(rest.len());
            rest[..cut].trim().to_string()
        } else {
            String::new()
        };
        out.push(NewsArticle {
            id,
            title,
            header,
            text: String::new(),
            updated_at,
            author: NewsAuthor {
                id: 0,
                user: author,
                role: role_from_class(window),
            },
        });
        from = start + 1;
    }
    out
}

fn date_from_window(window: &str) -> String {
    if let Some(rel) = window.find(">on ") {
        let rest = &window[rel + 4..];
        let date: String = rest.chars().take(10).collect();
        if date.len() == 10 {
            return date;
        }
    }
    if let Some(inner) = inner_text(window, "page-home-news-date", "</time>") {
        let stripped = strip_tags(inner);
        let date = stripped.trim_start_matches("on ").trim();
        if date.len() >= 10 {
            return date.chars().take(10).collect();
        }
    }
    String::new()
}

#[cfg(test)]
pub fn attach_news_ids(articles: &mut [NewsArticle], ids: &[(i64, String)]) {
    let mut used = vec![false; ids.len()];
    for article in articles.iter_mut() {
        if article.id > 0 {
            continue;
        }
        if let Some((index, (id, _))) = ids
            .iter()
            .enumerate()
            .find(|(i, (_, title))| !used[*i] && title == &article.title)
        {
            article.id = *id;
            used[index] = true;
        }
    }
}

fn inner_text<'a>(block: &'a str, open: &str, close: &str) -> Option<&'a str> {
    let start = block.find(open)? ;
    let after_open = &block[start + open.len()..];
    let gt = after_open.find('>')?;
    let rest = &after_open[gt + 1..];
    let end = rest.find(close)?;
    Some(rest[..end].trim())
}

/// Article `message-body` until the comment form. Drops the relative timeago line.
pub fn parse_news_entry_body(html: &str) -> String {
    let Some(start_at) = html.find("disable-message-border") else {
        return String::new();
    };
    let after_class = &html[start_at..];
    let Some(gt) = after_class.find('>') else {
        return String::new();
    };
    let mut inner = &after_class[gt + 1..];
    if let Some(form) = inner.find("<form") {
        inner = &inner[..form];
    }
    if let Some(comment) = inner.find("id=\"comment-") {
        inner = &inner[..comment];
    }
    let mut body = inner.trim().to_string();
    if let Some(time) = body.find("<time") {
        if body[time..].contains("data-type=\"medium\"") {
            if let Some(strong) = body[..time].rfind("<strong") {
                if let Some(end) = body[time..].find("</strong>") {
                    let end = time + end + "</strong>".len();
                    body.replace_range(strong..end, "");
                }
            }
        }
    }
    body.trim().to_string()
}

/// Comment cards: `id="comment-{id}"`, author, UTC stamp, `p-4` body.
pub fn parse_news_comments(html: &str) -> Vec<NewsComment> {
    let mut out = Vec::new();
    let mut from = 0usize;
    let marker = "id=\"comment-";
    while let Some(rel) = html[from..].find(marker) {
        let start = from + rel + marker.len();
        let rest = &html[start..];
        let digits: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
        let Ok(id) = digits.parse::<i64>() else {
            from = start.max(from + 1);
            continue;
        };
        let next = html[start..]
            .find(marker)
            .map(|n| start + n)
            .unwrap_or(html.len());
        let block = &html[start..next];
        let author = author_from_comment(block);
        let posted_at = inner_text(block, "text-align:end", "</div>")
            .unwrap_or("")
            .to_string();
        let body = inner_text(block, "p-4", "</div>")
            .unwrap_or("")
            .trim()
            .to_string();
        if id > 0 {
            out.push(NewsComment {
                id,
                author,
                posted_at,
                body,
                role: comment_role(block),
            });
        }
        from = next;
    }
    out
}

fn role_from_class(classy: &str) -> String {
    if classy.contains("is-color-staff") {
        "staff".into()
    } else if classy.contains("is-color-dj") {
        "dj".into()
    } else if classy.contains("is-color-dev") {
        "dev".into()
    } else {
        String::new()
    }
}

fn comment_role(block: &str) -> String {
    let Some(ml) = block.find("ml-1") else {
        return String::new();
    };
    let tag_start = block[..ml].rfind('<').unwrap_or(0);
    let tag = &block[tag_start..];
    let end = tag.find('>').unwrap_or(tag.len());
    role_from_class(&tag[..end])
}

fn author_from_comment(block: &str) -> String {
    let Some(ml) = block.find("ml-1") else {
        return String::new();
    };
    let rest = &block[ml..];
    let Some(gt) = rest.find('>') else {
        return String::new();
    };
    let inner = &rest[gt + 1..];
    let end = inner.find("</div>").unwrap_or(inner.len());
    strip_tags(&inner[..end])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_news_fixture_keeps_html_and_author() {
        let articles = parse_news(include_str!("../tests/fixtures/news.json")).unwrap();
        assert_eq!(articles.len(), 3);
        assert_eq!(articles[0].id, 0);
        assert_eq!(articles[0].title, "V/a/lentines Stream 2026");
        assert_eq!(articles[0].updated_at, "2026-02-14 13:37:34");
        assert_eq!(articles[0].author.user, "claud");
        assert!(articles[1]
            .text
            .contains("https://static.r-a-d.io/exci/2025-image.png"));
    }

    #[test]
    fn attach_ids_matches_titles_in_list_order() {
        let mut articles = parse_news(include_str!("../tests/fixtures/news.json")).unwrap();
        let ids = parse_news_ids(include_str!("../tests/fixtures/news_list.html"));
        attach_news_ids(&mut articles, &ids);
        assert_eq!(articles[0].id, 82);
        assert_eq!(articles[1].id, 81);
    }
}
