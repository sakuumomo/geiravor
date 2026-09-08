//! News list + article HTML. See `docs/spec/news.md`.

use crate::error::ApiError;
use crate::html::{between, strip_tags};
use crate::parse::check_bound;

pub const NEWS_URL: &str = "https://r-a-d.io/news";
pub const BOARD_NEWS_LABEL: &str = "News";

#[uniffi::export]
pub fn board_news_label() -> String {
    BOARD_NEWS_LABEL.to_string()
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NewsCard {
    pub id: i64,
    pub title: String,
    pub author: String,
    pub date: String,
    pub header: String,
    pub role: RoleColor,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NewsList {
    pub page: u32,
    pub last_page: u32,
    pub cards: Vec<NewsCard>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum RoleColor {
    None,
    Staff,
    Dj,
    Dev,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NewsComment {
    pub id: i64,
    pub author: String,
    pub when_utc: String,
    pub body: String,
    pub role: RoleColor,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NewsArticle {
    pub id: i64,
    pub title: String,
    pub author: String,
    pub body: String,
    pub comments: Vec<NewsComment>,
    pub role: RoleColor,
}

pub fn news_list_url(page: u32) -> String {
    if page <= 1 {
        NEWS_URL.to_string()
    } else {
        format!("{NEWS_URL}?page={page}")
    }
}

pub fn news_article_url(id: i64) -> String {
    format!("{NEWS_URL}/{id}")
}

pub fn parse_news_list(html: &str, page: u32) -> Result<NewsList, ApiError> {
    check_bound(html.as_bytes())?;
    let mut cards = Vec::new();
    let mut rest = html;
    while let Some(i) = rest.find("href=\"/news/") {
        rest = &rest[i + 12..];
        let id_s: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
        let Ok(id) = id_s.parse::<i64>() else {
            continue;
        };
        if id == 0 {
            continue;
        }
        let chunk = rest.get(..2500).unwrap_or(rest);
        if !chunk.contains("news-title") {
            continue;
        }
        let title = between(chunk, "news-title", "</span>")
            .map(|s| strip_tags(&s[s.find('>').map(|x| x + 1).unwrap_or(0)..]))
            .unwrap_or_default();
        let author = between(chunk, "page-home-news-author", "<time")
            .and_then(|s| s.rsplit('>').next())
            .map(strip_tags)
            .unwrap_or_default();
        let role = role_near(chunk, "page-home-news-author");
        let date = between(chunk, "page-home-news-date", "</time>")
            .map(|s| strip_tags(&s[s.find('>').map(|x| x + 1).unwrap_or(0)..]))
            .unwrap_or_default()
            .trim_start_matches("on ")
            .to_string();
        let header = between(chunk, "message-body", "</div>")
            .map(strip_tags)
            .unwrap_or_default();
        if title.is_empty() {
            continue;
        }
        cards.push(NewsCard {
            id,
            title,
            author,
            date,
            header,
            role,
        });
    }
    let mut last_page = 1u32;
    let mut pager = html;
    while let Some(i) = pager.find("/news?page=") {
        pager = &pager[i + 11..];
        let digits: String = pager.chars().take_while(|c| c.is_ascii_digit()).collect();
        if let Ok(n) = digits.parse::<u32>() {
            last_page = last_page.max(n);
        }
    }
    if cards.is_empty() {
        last_page = page.max(1).saturating_sub(1).max(1);
    } else if cards.len() < 20 {
        last_page = last_page.max(page.max(1));
    }
    Ok(NewsList {
        page: page.max(1),
        last_page: last_page.max(1),
        cards,
    })
}

pub fn parse_news_article(html: &str, id: i64) -> Result<NewsArticle, ApiError> {
    check_bound(html.as_bytes())?;
    let single = html
        .find("page-news-single")
        .map(|i| &html[i..])
        .unwrap_or(html);
    let title = between(single, "news-title", "</span>")
        .map(|s| strip_tags(&s[s.find('>').map(|x| x + 1).unwrap_or(0)..]))
        .unwrap_or_default();
    let header_span = between(single, "message-header", "</div>").unwrap_or("");
    let author = strip_tags(
        header_span
            .rsplit("<span")
            .next()
            .and_then(|s| s.find('>').map(|i| &s[i + 1..]))
            .unwrap_or(""),
    );
    let body_raw = between(single, "message-body", "</div>").unwrap_or("");
    let body = article_body(body_raw);
    let comments = parse_comments(single);
    Ok(NewsArticle {
        id,
        title,
        author,
        body,
        comments,
        role: role_from(header_span),
    })
}

fn article_body(raw: &str) -> String {
    let mut s = raw.to_string();
    while let Some(i) = s.find("data-type=\"medium\"") {
        let start = s[..i].rfind("<time").unwrap_or(i);
        let end = s[i..].find("</time>").map(|e| i + e + 7).unwrap_or(s.len());
        s.replace_range(start..end, "");
    }
    let s = s.replace("<strong></strong>", "");
    sanitize_news_html(&s)
}

/// Keep `p`, `br`, `em`, `strong`, `img` (static.r-a-d.io only), `time`, `a` `#comment-`.
pub fn sanitize_news_html(raw: &str) -> String {
    let mut out = String::new();
    let mut rest = raw;
    while let Some(lt) = rest.find('<') {
        out.push_str(&rest[..lt]);
        rest = &rest[lt..];
        let Some(gt) = rest.find('>') else {
            break;
        };
        let tag = &rest[..=gt];
        rest = &rest[gt + 1..];
        let lower = tag.to_ascii_lowercase();
        if lower.starts_with("<p")
            || lower.starts_with("</p")
            || lower.starts_with("<br")
            || lower.starts_with("<em")
            || lower.starts_with("</em")
            || lower.starts_with("<strong")
            || lower.starts_with("</strong")
            || lower.starts_with("<time")
            || lower.starts_with("</time")
        {
            out.push_str(tag);
        } else if lower.starts_with("<img") {
            if let Some(src) = crate::html::between(tag, "src=\"", "\"")
                && allow_news_img(src)
            {
                out.push_str(tag);
            }
        } else if lower.starts_with("<a ") {
            if tag.contains("href=\"#comment-") {
                out.push_str(tag);
            }
        } else if lower.starts_with("</a") {
            out.push_str(tag);
        } else if is_block_tag(&lower) && !lower.starts_with("</") {
            out.push_str("<br>");
        }
    }
    out.push_str(rest);
    trim_leading_breaks(&out)
}

fn trim_leading_breaks(s: &str) -> String {
    let mut t = s.trim_start();
    loop {
        let lower = t.to_ascii_lowercase();
        if !lower.starts_with("<br") {
            break;
        }
        let Some(gt) = t.find('>') else {
            break;
        };
        t = t[gt + 1..].trim_start();
    }
    t.to_string()
}

fn is_block_tag(lower: &str) -> bool {
    lower.starts_with("<div")
        || lower.starts_with("<h1")
        || lower.starts_with("<h2")
        || lower.starts_with("<h3")
        || lower.starts_with("<h4")
        || lower.starts_with("<h5")
        || lower.starts_with("<h6")
        || lower.starts_with("<li")
        || lower.starts_with("<ul")
        || lower.starts_with("<ol")
        || lower.starts_with("<blockquote")
        || lower.starts_with("<section")
        || lower.starts_with("<hr")
}

fn parse_comments(html: &str) -> Vec<NewsComment> {
    let mut out = Vec::new();
    let mut rest = html;
    while let Some(i) = rest.find("id=\"comment-") {
        rest = &rest[i + 12..];
        let id_s: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
        let Ok(id) = id_s.parse::<i64>() else {
            continue;
        };
        let chunk = rest.get(..2000).unwrap_or(rest);
        let role = role_from(chunk);
        let author = between(chunk, "ml-1", "</div>")
            .map(strip_tags)
            .unwrap_or_default();
        let when_utc = between(chunk, "text-align:end", "</div>")
            .map(|s| strip_tags(&s[s.find('>').map(|x| x + 1).unwrap_or(0)..]))
            .unwrap_or_default();
        let body = between(chunk, "class=\"p-4\"", "</div>")
            .map(sanitize_news_html)
            .unwrap_or_default();
        out.push(NewsComment {
            id,
            author,
            when_utc,
            body,
            role,
        });
    }
    out
}

fn allow_news_img(src: &str) -> bool {
    src.starts_with("https://static.r-a-d.io/") || src.starts_with("//static.r-a-d.io/")
}

fn absolute_news_img(src: &str) -> String {
    if src.starts_with("//") {
        format!("https:{src}")
    } else {
        src.to_string()
    }
}

/// `static.r-a-d.io` image srcs in article/comment HTML (https, protocol-relative folded).
pub fn news_image_urls(html: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut rest = html;
    while let Some(i) = rest.find("<img") {
        rest = &rest[i..];
        let Some(gt) = rest.find('>') else {
            break;
        };
        let tag = &rest[..=gt];
        rest = &rest[gt + 1..];
        if let Some(src) = crate::html::between(tag, "src=\"", "\"")
            && allow_news_img(src)
        {
            out.push(absolute_news_img(src));
        }
    }
    out
}

/// URLs in `old` that are not in `new`. Coil should drop those files.
pub fn dropped_news_images(old: &[String], new: &[String]) -> Vec<String> {
    old.iter()
        .filter(|u| !new.iter().any(|n| n == *u))
        .cloned()
        .collect()
}

#[uniffi::export]
pub fn news_image_urls_for(html: String) -> Vec<String> {
    news_image_urls(&html)
}

#[uniffi::export]
pub fn dropped_news_images_for(old: Vec<String>, new: Vec<String>) -> Vec<String> {
    dropped_news_images(&old, &new)
}

fn role_from(html: &str) -> RoleColor {
    if html.contains("is-color-staff") {
        RoleColor::Staff
    } else if html.contains("is-color-dj") {
        RoleColor::Dj
    } else if html.contains("is-color-dev") {
        RoleColor::Dev
    } else {
        RoleColor::None
    }
}

fn role_near(html: &str, marker: &str) -> RoleColor {
    let Some(i) = html.find(marker) else {
        return RoleColor::None;
    };
    let start = i.saturating_sub(80);
    let end = (i + marker.len() + 160).min(html.len());
    role_from(&html[start..end])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn list_cards_and_pager() {
        let list = parse_news_list(include_str!("../tests/fixtures/news_list.html"), 1).unwrap();
        assert!(list.cards.len() >= 10);
        assert_eq!(list.cards[0].id, 82);
        assert!(list.cards[0].title.contains("lentines"));
        assert_eq!(list.cards[0].author, "claud");
        assert_eq!(list.cards[0].date, "2026-02-02");
        assert_eq!(list.cards[0].role, RoleColor::None);
        assert!(list.last_page >= 4);
        assert!(!list.cards[0].header.is_empty());
    }

    #[test]
    fn article_skips_timeago_and_reads_comments() {
        let a =
            parse_news_article(include_str!("../tests/fixtures/news_article.html"), 82).unwrap();
        assert_eq!(a.id, 82);
        assert!(a.title.contains("lentines"));
        assert!(!a.body.contains("217 days"));
        assert!(a.body.contains("Good evening"));
        assert_eq!(a.role, RoleColor::None);
        assert!(a.comments.iter().any(|c| c.id == 5282));
        let staff = a.comments.iter().find(|c| c.id == 5279).unwrap();
        assert_eq!(staff.role, RoleColor::Staff);
        assert!(staff.author.contains("Ojiisan"));
        assert!(staff.body.contains("5276"));
    }

    #[test]
    fn sanitize_drops_leading_block_breaks() {
        assert_eq!(sanitize_news_html("<div></div><p>Hi</p>"), "<p>Hi</p>");
        assert!(!sanitize_news_html("<section></section>Hello").starts_with('<'));
    }

    #[test]
    fn empty_html_page_is_past_last() {
        let list = parse_news_list("<html></html>", 9).unwrap();
        assert!(list.cards.is_empty());
        assert_eq!(list.last_page, 8);
    }

    #[test]
    fn sanitize_keeps_static_images_drops_the_rest() {
        let out = sanitize_news_html(
            r#"<p>Hi</p><img src="https://static.r-a-d.io/x.jpg"><img src="https://evil.example/x.jpg"><script>alert(1)</script>"#,
        );
        assert!(out.contains("static.r-a-d.io/x.jpg"));
        assert!(!out.contains("evil.example"));
        assert!(!out.contains("script"));
        assert!(out.contains("<p>Hi</p>"));
        assert!(sanitize_news_html(r#"<img src="//static.r-a-d.io/y.png">"#).contains("y.png"));
        let blocks = sanitize_news_html("<div>One</div><div>Two</div>");
        assert!(blocks.contains("One"));
        assert!(blocks.contains("Two"));
        assert!(blocks.contains("<br>"));
    }

    #[test]
    fn news_image_urls_keep_static_and_drop_gone() {
        let old = news_image_urls(
            r#"<img src="https://static.r-a-d.io/a.jpg"><img src="https://evil.example/x.jpg"><img src="//static.r-a-d.io/b.gif">"#,
        );
        assert_eq!(
            old,
            vec![
                "https://static.r-a-d.io/a.jpg".to_string(),
                "https://static.r-a-d.io/b.gif".to_string(),
            ]
        );
        let new = news_image_urls(r#"<img src="https://static.r-a-d.io/b.gif">"#);
        assert_eq!(
            dropped_news_images(&old, &new),
            vec!["https://static.r-a-d.io/a.jpg".to_string()]
        );
    }

    #[test]
    fn list_and_article_bylines_take_role_class() {
        let list = parse_news_list(
            r#"<a href="/news/9"><span class="news-title">Hi</span>
            <span class="page-home-news-author is-color-dj">Hanyuu<time class="page-home-news-date">on 2026-01-01</time></span>
            <div class="message-body">flavor</div></a>"#,
            1,
        )
        .unwrap();
        assert_eq!(list.cards[0].role, RoleColor::Dj);
        let article = parse_news_article(
            r#"<div class="page-news-single"><div class="message-header"><span class="news-title">T</span>
            <span class="is-color-staff">claud</span></div>
            <div class="message-body"><p>Hi</p></div></div>"#,
            9,
        )
        .unwrap();
        assert_eq!(article.role, RoleColor::Staff);
    }
}
