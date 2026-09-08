//! UI windows over server pages. See `docs/spec/news.md` and `docs/spec/requests-faves.md`.

/// News HTML page size. The app does not show this many rows.
pub const NEWS_SERVER_SIZE: u32 = 20;
/// Search JSON page size.
pub const SEARCH_SERVER_SIZE: u32 = 20;
/// Favorites JSON page size.
pub const FAVES_SERVER_SIZE: u32 = 100;

/// One slice of a server page that fills part of a UI window.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServerSpan {
    pub page: u32,
    pub skip: u32,
    pub take: u32,
}

/// UI last page. Empty catalog is still one (empty) page.
pub fn ui_last_page(total: u32, fit: u32) -> u32 {
    let fit = fit.max(1);
    if total == 0 { 1 } else { total.div_ceil(fit) }
}

/// Items in a catalog whose last server page has `last_len` rows.
pub fn catalog_total(last_server: u32, last_len: u32, server_size: u32) -> u32 {
    let last_server = last_server.max(1);
    let server_size = server_size.max(1);
    let last_len = last_len.min(server_size);
    (last_server - 1) * server_size + last_len
}

/// Server pages to load for UI page `ui_page` (1-based).
pub fn server_spans(ui_page: u32, fit: u32, total: u32, server_size: u32) -> Vec<ServerSpan> {
    let fit = fit.max(1);
    let server_size = server_size.max(1);
    if total == 0 {
        return Vec::new();
    }
    let last_ui = ui_last_page(total, fit);
    let page = ui_page.max(1).min(last_ui);
    let start = (page - 1) * fit;
    if start >= total {
        return Vec::new();
    }
    let end = (start + fit).min(total);
    let mut spans = Vec::new();
    let mut i = start;
    while i < end {
        let server = i / server_size + 1;
        let skip = i % server_size;
        let take = (end - i).min(server_size - skip);
        spans.push(ServerSpan {
            page: server,
            skip,
            take,
        });
        i += take;
    }
    spans
}

/// Concatenate `skip`/`take` slices from already-fetched server pages.
pub fn take_window<T: Clone, F>(mut page_items: F, spans: &[ServerSpan]) -> Vec<T>
where
    F: FnMut(u32) -> Vec<T>,
{
    let mut out = Vec::new();
    for span in spans {
        let items = page_items(span.page);
        let skip = span.skip as usize;
        let take = span.take as usize;
        if skip >= items.len() {
            continue;
        }
        let end = (skip + take).min(items.len());
        out.extend(items[skip..end].iter().cloned());
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[test]
    fn first_page_does_not_stitch() {
        assert_eq!(
            server_spans(1, 7, 20, 20),
            vec![ServerSpan {
                page: 1,
                skip: 0,
                take: 7
            }]
        );
    }

    #[test]
    fn last_ui_page_is_the_leftover() {
        assert_eq!(ui_last_page(20, 7), 3);
        assert_eq!(
            server_spans(3, 7, 20, 20),
            vec![ServerSpan {
                page: 1,
                skip: 14,
                take: 6
            }]
        );
    }

    #[test]
    fn window_stitches_across_a_server_boundary() {
        assert_eq!(ui_last_page(25, 7), 4);
        assert_eq!(
            server_spans(3, 7, 25, 20),
            vec![
                ServerSpan {
                    page: 1,
                    skip: 14,
                    take: 6
                },
                ServerSpan {
                    page: 2,
                    skip: 0,
                    take: 1
                },
            ]
        );
        let mut pages = HashMap::new();
        pages.insert(1u32, (0..20).collect::<Vec<_>>());
        pages.insert(2, (20..25).collect());
        let spans = server_spans(3, 7, 25, 20);
        let got = take_window(|p| pages.get(&p).cloned().unwrap_or_default(), &spans);
        assert_eq!(got, vec![14, 15, 16, 17, 18, 19, 20]);
    }

    #[test]
    fn does_not_span_a_server_page_past_total() {
        let s = server_spans(3, 7, 20, 20);
        assert!(s.iter().all(|x| x.page == 1));
    }

    #[test]
    fn leftover_catalog_count() {
        assert_eq!(catalog_total(5, 3, 20), 83);
        assert_eq!(catalog_total(1, 10, 20), 10);
        assert_eq!(catalog_total(2, 100, 100), 200);
    }

    #[test]
    fn empty_catalog_is_one_ui_page() {
        assert_eq!(ui_last_page(0, 7), 1);
        assert!(server_spans(1, 7, 0, 20).is_empty());
    }
}
