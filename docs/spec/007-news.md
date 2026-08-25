# 007 — News (0.2.0)

Not built in 0.1.0.

`GET https://r-a-d.io/api/news` returns an array (typically 3):

```
{ title, header, text, updated_at, author: { id, user } }
```

`header` and `text` are **HTML** (paragraphs, `<br>`, `<em>`, `<img>`). Images may be on `https://static.r-a-d.io/` — allow that host in 0.2.0.

`updated_at` is a naive datetime string (`"2026-02-14 13:37:34"`). The homepage uses unix seconds in a `datetime` attribute; prefer displaying `updated_at` as a date or parse if TZ is documented later.

List + article. Do not render untrusted HTML in a full-site WebView; use a constrained HTML renderer.

Not in Android Auto.
