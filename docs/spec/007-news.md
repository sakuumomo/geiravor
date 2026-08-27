# 007 — News (0.2.0)

`GET https://r-a-d.io/api/news` is a **homepage snapshot** (typically 3) and has **no post id**. It is not the News tab list.

## List (paginated)

HTML only: `GET https://r-a-d.io/news` (page 1) and `GET https://r-a-d.io/news?page=` (2…). The site’s HTML page size is **20**; the app **does not show 20**. The list page is sized to the pane: as many cards as fit **without scrolling**, then `PageTabs` over that window (may span two HTML pages). Last HTML page is the max `/news?page=` in that HTML, or the current page when the page is short.

Each card: `href="/news/{id}"`, `news-title`, author, date (`<time>` inner `on YYYY-MM-DD`), `header` flavor in `message-body`. Do **not** repeat `header` in the article.

`RadioCore` caches list pages + last page in process memory. Kotlin only remembers which page the UI is on.

## Article

`GET https://r-a-d.io/news/{id}` is HTML: body + comments + gorilla CSRF. One GET fills both. Cache the parsed body and comments (invalidate comments on a successful POST).

The article is the `message-body` HTML (paragraphs, `<br>`, `<em>`, `<strong>`, `<img>`, `<time>`), not the list flavor. Skip the relative `data-type="medium"` timeago line.

`<time datetime="{unix}" data-type="local" data-dur="{ms}">` is converted to the device local timezone the same way the site’s JS does (`data-dur` is a range when non-zero). Do not leave the inner UTC `+0000` string, and do not substitute the raw unix value.

Images may be on `https://static.r-a-d.io/` — that host is allowed in network security config. Load with Coil, **fit the article column width** (not the full display width). Other image hosts are dropped.

Do not render untrusted HTML in a full-site WebView. Prefer Compose / `HtmlCompat` (inline markup only) + Coil for images.

## Comments

No JSON. Same `GET /news/{id}` HTML. `POST https://r-a-d.io/news/{id}` with cookie `_gorilla_csrf` + header `X-CSRF-Token` + form `comment=` (markdown, max **500**). Empty body is invalid. Anonymous when not logged in. After POST, re-read comments from the HTML response.

Do **not** paginate comments; one scroll is enough.

Show author (`Anonymous (abcd)` or nick, including `## staff`), `YYYY-MM-DD HH:MM:SS UTC`, `#id`. Staff / DJ / dev names use the site role colors (`is-color-staff` green, `is-color-dj` blue, `is-color-dev` red) on comments and article/list bylines.

Imageboard clicks:

- **`#id`** (the comment id) quotes `>>id\n` into the composer (append; keep a trailing newline).
- **`>>id`** in a body (`href="#comment-{id}"`) **jumps** to that comment. Do not open a browser.

Not in Android Auto.
