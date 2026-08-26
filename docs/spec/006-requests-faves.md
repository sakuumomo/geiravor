# 006 — Requests and favorites (0.2.0)

Not built in 0.1.0. Specced so `ApiClient` (`core`) can grow without a rewrite. Add-fave is **not** an `ApiClient` HTTP method.

UI noun is **Favorites**. Verb / Auto command is **Fave**. Do not label anything **Faves**.

## Search

`GET https://r-a-d.io/api/search/{query}?page=`

Query is a **path** segment: percent-encode. Default page size 20.

Response: `{ total, per_page, current_page, last_page, from, to, data: [{ artist, title, id, lastplayed, lastrequested, requestable }] }`.

`id` is `TrackID` for `POST /request/{id}`. `artist` / `title` are already split (do not run `np` split).

The site navbar uses HTML `GET /v1/search` — the app does not.

## Can-request

`GET https://r-a-d.io/api/can-request` → `{"Main":{"requests":true}}` with capital **`Main`**. Do not reuse the `/api` lowercase `main` decoder blindly.

Keyed on client IP. Carrier NAT shares cooldown. Empty/false `requests` means the AFK streamer is off or the IP is cooling down.

Also honor `main.requesting` / `isafkstream` from the snapshot: no requests during a live DJ.

## Request

`POST https://r-a-d.io/request/{TrackID}`

Production uses **gorilla csrf**. CSRF is **not** skipped on `/request`. There is no JSON token endpoint. Do not copy an old app’s `<form` scraper. Do not GET `/v1/search` (navbar HTML).

**Token source (spiked 2026-08-26 against live r-a-d.io):**

| Piece | Where |
|---|---|
| Cookie `_gorilla_csrf` | `Set-Cookie` on almost every GET, including `GET /api` (HttpOnly; Secure; SameSite=Lax; 7 days). Cookie jar already on the process-wide client. The cookie value is **not** the header token. |
| Header `X-CSRF-Token` | **Not** returned on `GET /api`, `/`, `/help`, `/news`. Live HTML embeds `<input type="hidden" name="gorilla.csrf.Token" value="…">`. Cheapest page that has it: **`GET https://r-a-d.io/search`** (as of the spike, that input sits in an HTML comment; extract the attributes, do not parse the document). `GET /v1/search` also embeds it in request buttons — the app still does not use that path. |

Bootstrap: `GET /search` on the **same** reqwest client as the later POST so the jar’s cookie matches that page’s token. Then `POST https://r-a-d.io/request/{TrackID}` with cookie (jar) + `X-CSRF-Token: <extracted>`. A compatibility shim also accepts JSON `{ "_token": "..." }`; 0.2.0 sends the header. On CSRF 403, GET `/search` again and retry once.

Extract is a one-field look for `name="gorilla.csrf.Token"` then `value="…"` — not a general HTML client, not a WebView.

JSON error/success keys from the legacy POST: `success` or `error` strings (song cooldown, user cooldown, requests disabled).

This CSRF path is **request only**. There is no `POST /faves`.

## Favorites list

No site login. User types their public **Rizon** nick.

`GET https://r-a-d.io/faves?nick=&page=&dl=true` → array of `{ tracks_id, meta, lastrequested, lastplayed, requestcount }` with nulls. `meta` is `"Artist - Title"`. Page size 100. Unknown nick → `[]`, not an error.

Last page is **not** in that JSON. `GET https://r-a-d.io/faves?nick=` (HTML, no `dl`) has pagination links; take the max `/faves?…page=` (ignore `/v1/request?…page=`). Past-last JSON `page=` **clamps** to the last page instead of `[]`. Probe fingerprints only if the HTML parse is 1 and page 1 was full.

`RadioCore` caches search pages and faves pages + last page in process memory. Prefetch page 1 + last page when the stored nick is known. Kotlin only remembers which page/query the UI is on.

Persist nick in DataStore. Empty nick → empty list, not a crash. That nick is also the direct-Rizon `NICK` and must match the nick Hanyuu will attribute (the bouncer’s existing Rizon nick when using a bouncer).

## Add-fave (IRC)

The station records a favorite when an identified Rizon nick `PRIVMSG`s `Hanyuu-sama`. Hanyuu (valkyrie) does **not** special-case live DJ vs AFK for these commands:

| Command | Effect |
|---|---|
| `.fave` | Current on-air, via status metadata → catalog song |
| `.fave last` | Station last-played |
| `.fave <id>` | That catalog `trackid` |
| `.unfave` / `.unfave last` / `.unfave <id>` | Matching removes |

Replies **name** the song, e.g. `Added 'Artist - Title' to your favorites.` / already-favorited / unknown ID. Strip IRC color codes before matching. Show that named title to the user (phone). Auto must not rewrite now-playing metadata to display it.

This is **not** an IRC client: no channel UI, no JOIN, no chat log, no native Quassel, no SASL in 0.2.0.

Fave while paused is allowed (Hanyuu faves station state, not local playback). Snapshot **at tap**: `isafkstream`, `trackid`, `np`. Empty/whitespace nick → no connect, no-op on Auto.

One in-flight fave. Blocking worker thread — never the `/api` poller, never the Android main thread. After `001`, sample the process-wide latest snapshot already in `RadioCore` (no extra GET).

### Accuracy — AFK (`isafkstream` at tap, `trackid > 0`)

Send `.fave {trackid}`. No delay, no `.fave last`, no unfave loop. Already-favorited for that title is success. If a live DJ takes over during the handshake, still send that ID. AFK with `trackid == 0` uses the live-DJ machine.

### Accuracy — live DJ (`isafkstream == false` at tap)

Never use `trackid` (it can be leftover AFK). One session; correct in-place; then tear down.

1. Intended `np` from the tap snapshot (normalized).
2. After `001` (+ identify on direct Rizon), read latest `np`.
3. If it still matches intended → `.fave`. If it changed → `.fave last`.
4. Hanyuu’s named song vs intended `np`. Match, including already-favorited of the intended title → success. Not-in-DB / unknown → fail, nothing to undo.
5. Mismatch: undo only if the reply was **Added**. Do not `.unfave` an already-favorited wrong title. If the first command was `.fave` and Added the wrong current: `.unfave` then `.fave last`. If that is also Added-wrong: `.unfave last` and fail. If we already started with `.fave last` and it was wrong: `.unfave last` if Added, then fail. No `.fave last last`.

`.fave last` is the live DJ’s last played song (station last-played), not leftover AFK.

### Direct Rizon

`irc.rizon.net:6697`, TLS, rustls + webpki-roots, **always verify**. `NICK <public nick>`, `USER geiravor 0 * :Geiravor`, optional `PRIVMSG NickServ :IDENTIFY <password>` (wait ~5s; invalid → fail; no reply → continue). After the fave machine: `QUIT :Geiravor` then close. `433` nick in use → fail visibly (do not ghost / `NICK nick_`).

### Generic bouncer (ZNC / soju / similar)

TLS required. Optional server `PASS` (e.g. ZNC `user/network:pass` as an opaque string). Host + port (default 6697). Do not special-case ZNC vs soju. Not Quassel.

The bouncer is **already** on Rizon with its nick (NickServ already done). Least possible traffic:

- `PASS` if set (bouncer auth only).
- Handshake `NICK geiravor-<short>` / `USER geiravor 0 * :Geiravor` to the **bouncer** (client id, not the public fave nick). Never `NICK` after `001` (that would rename the Rizon session). Never send the public nick as `NICK` here.
- No NickServ `IDENTIFY`.
- No `QUIT`, no `*status` / BouncerServ, no JOIN.
- `PRIVMSG Hanyuu-sama` only, then **orderly client detach**: TLS `close_notify` + TCP FIN. Not a RST/drop (ping-timeout would block a Fave ~30s later). The bouncer’s Rizon link stays up. A second fave is a new client attach.

**Allow insecure TLS** toggle (default **off**, DataStore, not a secret): bouncer IRC only, so a self-signed cert works. Label it as insecure. Do not offer this for r-a-d.io HTTP or `irc.rizon.net`.

### Secrets

NickServ password and bouncer `PASS`: EncryptedSharedPreferences (Tink). Never log them, never log `IDENTIFY` / `PASS` lines. Nick, host, port, profile, insecure-TLS flag: DataStore. UniFFI takes secrets in memory for the call only.

### Tests

Local only: `IrcIo` scripts and a localhost TLS listener that speaks 001 / PING / Hanyuu NOTICE. Do not open `irc.rizon.net` or r-a-d.io in CI. Cover AFK `.fave {id}`, live-DJ last/Added-only unfave, bouncer no-QUIT / no-NICK-after-001 / orderly close, self-signed fails unless the toggle is on.

## UI

Search results + favorites can request when `requestable` / AFK. Show server error strings. Cooldown from can-request + snapshot.

Search and favorites paginate. Fetch `?page=` (search) / `&page=` (faves). Bottom bar, last page > 1: previous `<` and next `>` stay pinned at the bar edges; first page, a sliding window of nearby pages, `...` (jump-to-page), last page sit in the middle. Page numbers use a width for the last page’s digit count so 9→10 does not shift prev/next. Search `last_page` is in the JSON. Faves last page: HTML pagination (above). Returning to Request or Favorites in the same process restores the cached query/nick, page, rows, and last page immediately.

Favorites connection expander: Rizon (default) vs Bouncer (host, port, server password, Allow insecure TLS). NickServ is Rizon-direct only.
