# API

Source of truth: live `https://r-a-d.io` and production JSON. Site path `/v1/` is **not** this app’s version.

## Endpoints

| Need | Method | URL | Notes |
|---|---|---|---|
| Snapshot / live | GET | `https://r-a-d.io/api` | JSON `{ "main": { … } }` |
| Stream | GET | `https://stream.r-a-d.io/main.mp3` | Playback only |
| DJ image | GET | `https://r-a-d.io/api/dj-image/{dj.djimage}` | GET-only (HEAD 405) |
| Search | GET | `/api/search/{query}?page=` | Query **in the path**, percent-encode |
| Can-request | GET | `/api/can-request` | `{"Main":{"requests":true}}` capital `Main` |
| Request | POST | `/request/{TrackID}` | CSRF; see [requests-faves.md](requests-faves.md) |
| Favorites list | GET | `/faves?nick=&page=&dl=true` | JSON; last page from HTML |
| News snapshot | GET | `/api/news` | Homepage only; **no id**; not the Board list |
| News list | GET | `/news`, `/news?page=` | HTML; [news.md](news.md) |
| News article + comments | GET/POST | `/news/{id}` | HTML; POST `comment=` + CSRF |
| Schedule | GET | `https://r-a-d.io/schedule` | HTML; no JSON |
| Staff | GET | `https://r-a-d.io/staff` | HTML; no JSON |
| Theme sniff | GET | `https://r-a-d.io/` | Cheap HTML; prefer `/assets/{name}/` already in hand |

Do **not** use `GET https://r-a-d.io/main` (301 to a protocol-relative relay). Do **not** use `GET /v1/sse`. Do **not** HEAD-probe Icecast (400). There is **no** `POST /faves` — add-fave is IRC. Do **not** GET theme CSS at runtime. `GET /api/ping` is not required for playback.

## `/api` envelope

Top-level object `{ "main": { ... } }` — lowercase `main`.

### Use

`np`, `listeners`, `isafkstream`, `current`, `start_time`, `end_time`, `trackid`, `thread`, `requesting`, `dj.id`, `dj.djname`, `dj.djimage`, `queue[].meta`, `queue[].timestamp`, `queue[].type`, `lp[].meta`, `lp[].timestamp`, `tags`

`tags` may be a string array, `[]`, or **`null`** (live DJ). Treat null / missing as no tags. A `null` must not fail the snapshot parse — that would leave the last AFK paint on screen.

### Ignore for UI

`bitrate` (live value is 0), `isstreamdesk`, `lastset` (naive datetime, no TZ), `djname` (username; display is `dj.djname`), `dj.css`, `dj.theme_id`, `dj.djcolor`, `queue[].time`, `lp[].time` (**HTML** `<time class="timeago">`).

Do **not** `deny_unknown_fields`. Extra keys must not break the app.

## Clocks

`current`, `start_time`, `end_time`, `timestamp` are **unix seconds**.

The website `data-start` and SSE `event: time` are **milliseconds**. The app never reads those.

Progress (AFK / `isafkstream == true` only):

```
elapsed = (local_now_secs + (server_current - local_at_fetch)) - start_time
```

Clamp to `[0, end_time - start_time]`. If `end_time <= start_time`, duration is unknown (indeterminate / hide bar).

Live DJ (`isafkstream == false`): ignore `start_time` / `end_time`. DJs mix live; catalog track lengths must not drive the progress bar or Media3 duration. Hide the phone progress bar and mm:ss (same as Auto: no bar). Thread embed sits under next/previous.

## `np` split

Split on the first `" - "`. Left = artist, right = title. If no separator, artist empty, title = full string. Trim. Tested examples: `Aimer with chelly (EGOIST) - ninelie`, `3L - ・－・・ －－－ ・・・－ ・`.

Same split applies to `queue[].meta` and `lp[].meta`.

## Queue / last played

- At most 5 entries as returned.
- Hide the queue in UI when `isafkstream == false` even if the array is non-empty.
- `type == 1` = user request (`/r/`); `0` = auto.
- Relative labels vs `current`: last played “N minutes ago”, queue “in N minutes”. Sub-minute last played: “<1 minute ago”.

## Thread

`thread == "none"` or empty → hidden. Hidden on Hanyuu (`isafkstream == true`) even if a leftover URL is present. Live DJ: `image:<url>` or an image URL is embedded (long-press save / open); any other `http(s)` URL is a browser link. Phone now-playing only, never Auto.

## Poll

Pure function of `(ui_visible, playing, last_error)`:

| State | Interval |
|---|---|
| UI visible or playing, last fetch ok | **2s** (floor; matches server cache) |
| Background, stopped, last fetch ok | 15s |
| Consecutive failures | exponential backoff, cap 60s |

ICY title change → immediate refetch. ICY string does **not** replace `np`.

Poller is process-wide.

## Stream-down

Not a magic `np`/`ICY` title. Stream-down = player error or HTTP failure on the stream. `/api` may still succeed. A successful `/api` snapshot must **not** clear stream-down. Clear it only when the player is actually playing again.

## DJ image

`https://r-a-d.io/api/dj-image/{dj.djimage}` e.g. `18-e0177611a37081b5.png`. May be PNG, GIF, MP4, or WebM. Coil GET for stills and GIF (animated); Media3 for mp4/webm, autoplay muted loop. Memory+disk cache keyed by `dj.djimage` (URL) for Coil. Fetch only when that id changes. Placeholder is the cached bitmap when present; mystery-DJ only on error/miss. Shade, Auto, and the DJ-online notifier use that same Coil cache flattened to a **still** (GIF first frame / current drawable frame). They cannot animate: Media3 artwork and notification large icons are bitmaps. Do not drop a GIF because Coil’s drawable is animated rather than a `BitmapDrawable`.

## Least network

- Schedule and staff GET **only when that Board section is opened** (lazy), then revalidate on open — not on a timer.
- Holiday sniff: process start while in a holiday window ([ui.md](ui.md)); if the site is not on that holiday yet, at most once per hour until it is; **once seen on, stop until the window ends**. App start may sniff again. Reuse HTML already in hand (news/schedule/staff) before `GET /`. Not every `/api` poll.
- Search listing stays off disk (process RAM of fetched pages). **Committed** favorites listing pages and membership stay on disk: show cache first, GET, write only if the payload actually changed, prune when those nicks change. Peeks stay HTTP + RAM.
- News list HTML, viewed article bodies, schedule week, staff list, last-paint: show cache first; write only if different; last-paint skip write when chrome is unchanged (`current` and `listeners` tick every poll and must not force a rewrite).
- Do not add If-None-Match unless a later spec says the site sends useful ETags.
- In-flight: one GET per URL (coalesce).
- Bounded HTML/JSON parse (size cap). Do not GET Icecast except to play.
