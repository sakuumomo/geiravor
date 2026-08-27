use geiravor_core::{
    NEWS_LIST_URL, NEWS_URL, news_entry_url, news_list_url, parse_news, parse_news_comments,
    parse_news_entry_body, parse_news_ids, parse_news_last_page, parse_news_list,
};

#[test]
fn news_url_is_json_api() {
    assert_eq!(NEWS_URL, "https://r-a-d.io/api/news");
}

#[test]
fn parse_news_fixture_keeps_html_and_author() {
    let articles = parse_news(include_str!("fixtures/news.json")).expect("parse");
    assert_eq!(articles.len(), 3);
    assert_eq!(articles[0].title, "V/a/lentines Stream 2026");
    assert_eq!(articles[0].updated_at, "2026-02-14 13:37:34");
    assert_eq!(articles[0].author.id, 23);
    assert_eq!(articles[0].author.user, "claud");
    assert!(articles[0].header.contains("<p>"));
    assert!(articles[1]
        .text
        .contains("https://static.r-a-d.io/exci/2025-image.png"));
    assert!(articles[1].text.contains("<em>"));
    assert_eq!(articles[2].author.user, "exci");
}

#[test]
fn parse_news_empty_array() {
    let articles = parse_news("[]").expect("parse");
    assert!(articles.is_empty());
}

#[test]
fn parse_news_rejects_non_array() {
    assert!(parse_news("{}").is_err());
    assert!(parse_news("not json").is_err());
}

#[test]
fn news_ids_come_from_html_list_not_json() {
    assert_eq!(NEWS_LIST_URL, "https://r-a-d.io/news");
    assert_eq!(news_list_url(1), "https://r-a-d.io/news");
    assert_eq!(news_list_url(2), "https://r-a-d.io/news?page=2");
    assert_eq!(news_entry_url(81), "https://r-a-d.io/news/81");
    let ids = parse_news_ids(include_str!("fixtures/news_list.html"));
    assert_eq!(
        ids,
        vec![
            (82, "V/a/lentines Stream 2026".into()),
            (81, "Holid/a/y Stre/a/ms 2025 Schedule".into()),
        ]
    );
}

#[test]
fn parse_news_list_has_flavor_author_date_and_last_page() {
    let html = include_str!("fixtures/news_list.html");
    let page = parse_news_list(html, 1);
    assert_eq!(page.current_page, 1);
    assert_eq!(page.last_page, 4);
    assert_eq!(page.data.len(), 2);
    assert_eq!(page.data[0].id, 82);
    assert_eq!(page.data[0].author.user, "claud");
    assert_eq!(page.data[0].updated_at, "2026-02-02");
    assert!(page.data[0].header.contains("God fuckin help me"));
    assert!(page.data[0].text.is_empty());
    assert_eq!(parse_news_last_page(html), 4);
}

#[test]
fn parse_news_entry_body_skips_timeago_and_keeps_html() {
    let html = include_str!("fixtures/news_entry.html");
    let body = parse_news_entry_body(html);
    assert!(body.contains("<em>As always</em>"));
    assert!(body.contains("https://static.r-a-d.io/exci/2025-image.png"));
    assert!(!body.contains("256 days"));
    assert!(!body.contains("comment-4807"));
}

#[test]
fn parse_news_comments_from_entry_html() {
    let comments = parse_news_comments(include_str!("fixtures/news_entry.html"));
    assert_eq!(comments.len(), 3);
    assert_eq!(comments[0].id, 4807);
    assert_eq!(comments[0].author, "Anonymous (ab25)");
    assert_eq!(comments[0].posted_at, "2026-04-14 04:21:01 UTC");
    assert!(comments[0].body.contains("ICU"));
    assert_eq!(comments[1].id, 4770);
    assert!(comments[1].body.contains("#comment-4807"));
    assert_eq!(comments[2].id, 5279);
    assert!(comments[2].author.contains("Ojiisan"));
    assert!(comments[2].author.contains("staff"));
    assert_eq!(comments[2].role, "staff");
    assert_eq!(comments[0].role, "");
}
