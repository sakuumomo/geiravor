# News

`GET https://r-a-d.io/api/news` is a **homepage snapshot** (typically 3) and has **no post id**. It is not the Board → News list.

Not in Android Auto.

## List (paginated)

HTML only: `GET https://r-a-d.io/news` (page 1) and `GET https://r-a-d.io/news?page=` (2…). The site’s HTML page size is **20**; the app **does not show 20**. UI pages slice a **flat catalog** of every HTML page: as many cards as fit **without scrolling** in the current list pane (same rule as Request / Favorites — measure the pane, fill it, do not guess a frozen portrait count). Only the true last leftover of the whole catalog may be short — stitch across HTML pages to fill the pane. Last HTML page is the max `/news?page=` in that HTML, or the current page when the page is short. An **empty** HTML page is past-last: do not take it as last even if the pager marks it current (Valkyrie still renders `?page=` past the last full page).

Each card: `href="/news/{id}"`, `news-title`, author, date (`<time>` inner `on YYYY-MM-DD`), `header` flavor in `message-body`. Do **not** repeat `header` in the article.

List pages, article bodies, and comments live on disk and in a process RAM catalog. Open list/article: **show disk/RAM first**, then revalidate. **Write only if the payload actually changed**. If it changed, update the screen; if it is the same, do not write and do not flash loading. Keep every HTML list page on disk (the catalog is small). Keep a viewed article body even after leaving the list. UI page 1 always re-GETs HTML page 1 after showing cache; if those ids changed, drop later HTML pages until they are fetched again. Other UI pages GET an HTML page only on a RAM/disk miss. Remember which UI page is selected.

## Article

`GET https://r-a-d.io/news/{id}` is HTML: body + comments + gorilla CSRF. One GET fills both. Persist body and comments; revalidate on open; **write only if different**. Replace comments on a successful POST. Drop Coil files for image URLs that leave the article.

The article is the `message-body` HTML (paragraphs, `<br>`, `<em>`, `<strong>`, `<img>`, `<time>`), not the list flavor. Skip the relative `data-type="medium"` timeago line. **← News** stays pinned at the top as a link over the scrolling article — not a toolbar or rule. A compact theme **film** chip sits behind the label: **partially transparent at rest**, opaque on hover (glass 50% black, Christmas white sheet, Default `surface`) so the pointer hover reads. The body and comments scroll under it. Extra pad between the article body and the first comment.

`<time datetime="{unix}" data-type="local" data-dur="{ms}">` is converted to the device local timezone the same way the site’s JS does (`data-dur` is a range when non-zero). Do not leave the inner UTC `+0000` string, and do not substitute the raw unix value.

Images may be on `https://static.r-a-d.io/` — that host is allowed in network security config. Load with Coil, **fit the article column width** (not the full display width). Other image hosts are dropped. Do **not** autoplay GIF/mp4/webm in news.

Do not render untrusted HTML in a full-site WebView. Prefer Compose / `HtmlCompat` (inline markup only) + Coil for images.

## Comments

No JSON. Same `GET /news/{id}` HTML. `POST https://r-a-d.io/news/{id}` with cookie `_gorilla_csrf` + header `X-CSRF-Token` + form `comment=` (markdown, max **500**). Empty body is invalid. Anonymous when not logged in. After POST, re-read comments from the HTML response.

Do **not** paginate comments; one scroll is enough.

Show author (`Anonymous (abcd)` or nick, including `## staff`), `YYYY-MM-DD HH:MM:SS UTC`, `#id`. Staff / DJ / dev names use the site role colors (`is-color-staff` green, `is-color-dj` blue, `is-color-dev` red) on comments and article/list bylines.

Imageboard clicks:

- **`#id`** (the comment id) appends `>>id` **after** a newline or space (never before). Empty composer: `>>id`. Already ends in newline or space: append `>>id`. Otherwise insert a newline, then `>>id`.
- **`>>id`** in a body (`href="#comment-{id}"`) **jumps** to that comment. Do not open a browser.
