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

The last JSON page is a full 100 when leftover is short: Valkyrie slides that window so it overlaps the previous page (same songs at the top, leftover mixed in). Drop any row already on the previous page; the last page is leftover only. If that leaves nothing, the last page was a clamp — `last_page` is the previous page.

Search and faves **listing** stay off disk. Process RAM holds fetched server pages so paging the same query/nick does not GET again (UI page 1 still re-GETs after showing RAM). Home-nick **membership** (`tracks_id` + `meta`) is Room + a small RAM set hydrated at start; revalidate on start and after a successful fave/unfave. Persist membership **only if the rows actually changed** (`DiskPolicy.changed`, `001-architecture.md`). Other nicks are never written. Peeking another nick in the Favorites field GETs that list only (heart / IRC list nick stay on home). IME Done — or the first nonempty nick — commits DataStore home and drops the previous membership. Request random GETs live on tap.

Persist nick in DataStore. Empty nick → empty list, not a crash. **Settings → Connection nick**, if nonempty, is the IRC nick for fave/unfave (direct-Rizon `NICK`, SASL username default, favorites overlay). The Favorites tab nick is only the public list (`GET /faves?nick=`). Empty connection nick falls back to the Favorites tab nick. Empty Favorites nick falls back to Connection nick for that list, heart, overlay, and membership — Hanyuu records `e.Source.Name`, which is the IRC nick. Empty both → empty list, IRC no-op. The IRC nick must match the nick Hanyuu will attribute (the bouncer’s existing Rizon nick when using a bouncer). Revalidate after a successful fave/unfave must not drop the in-process overlay until the GET for that nick actually includes (or omits) the song.

## Add-fave (IRC)

The station records a favorite when an identified Rizon nick `PRIVMSG`s `Hanyuu-sama`. Hanyuu (valkyrie) does **not** special-case live DJ vs AFK for these commands:

| Command | Effect |
|---|---|
| `.fave` | Current on-air, via status metadata → catalog song |
| `.fave last` | Station last-played |
| `.fave <id>` | That catalog `trackid` |
| `.unfave` / `.unfave last` / `.unfave <id>` | Matching removes |

Replies **name** the song, e.g. `Added 'Artist - Title' to your favorites.` / already-favorited / unknown ID / removed. Strip IRC color codes before matching. The named title is for accuracy (match the tap snapshot), not a phone banner. Heart fill/outline is the success feedback on phone, shade, and Auto. A failed or no-op attempt must not change the heart: no fill on a failed fave, no outline on a failed unfave. Show failures and empty-nick on the phone as red text; fade that out when now-playing `np` changes. Auto must not rewrite now-playing metadata.

The heart is a **toggle** shared by phone, the shade, and Auto. If the current song is already a favorite and a catalog `trackid` is known (AFK snapshot id, or a matching `/faves` row id — never leftover live-DJ `trackid`), send `.unfave {id}`. Fill/outline every surface together. Without a catalog id, unfave is not offered.

This is **not** an IRC client: no channel UI, no JOIN, no chat log, no native Quassel. 0.2.0 SASL is only for this short-lived fave session (`PLAIN` password and/or `EXTERNAL` with a TLS client certificate).

Fave while paused is allowed (Hanyuu faves station state, not local playback). Snapshot **at tap**: `isafkstream`, `trackid`, `np`. Empty/whitespace nick → no connect, no-op on Auto.

One in-flight fave. Blocking worker thread — never the `/api` poller, never the Android main thread. After `001`, sample the process-wide latest snapshot already in `RadioCore` (no extra GET). Heart / catalog match use the Favorites (list) nick; IRC uses Connection nick (fallback to list nick). On `Timeout` / `Network` / TLS drop, retry the whole session up to two extra times. Do not retry nick-in-use, nick mismatch, SASL, unknown id, or accuracy failure.

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

`irc.rizon.net:6697`, TLS, rustls + webpki-roots, **always verify**. `NICK <public nick>`, `USER geiravor 0 * :Geiravor`, optional `PRIVMSG NickServ :IDENTIFY <password>` (wait ~5s; invalid → fail; no reply → continue). After the fave machine: `QUIT :Geiravor` then close. `432`/`433`/`436`/`437` (erroneous, in use, collision, unavailable) → fail visibly (do not ghost / `NICK nick_`). After `001`, the assigned nick must be the `NICK` we sent (public nick on direct Rizon; `geiravor-<short>` on a bouncer). If the server renamed us (`nick_`, Guest, …) or sent `NICK` to a different name, fail visibly and do **not** `PRIVMSG` Hanyuu. Listing someone else’s `/faves` over HTTP is still allowed.

### Generic bouncer (ZNC / soju / similar)

TLS required. Optional server `PASS` (e.g. ZNC `user/network:pass` as an opaque string). Host + port (default 6697). Do not special-case ZNC vs soju. Not Quassel.

The bouncer is **already** on Rizon with its nick (NickServ already done). Least possible traffic:

- `PASS` if set (bouncer auth only).
- Handshake `NICK geiravor-<short>` / `USER geiravor 0 * :Geiravor` to the **bouncer** (client id, not the public fave nick). Never `NICK` after `001` (that would rename the Rizon session). Never send the public nick as `NICK` here.
- No NickServ `IDENTIFY`.
- No `QUIT`, no `*status` / BouncerServ, no JOIN.
- `PRIVMSG Hanyuu-sama` only, then **orderly client detach**: TLS `close_notify` + TCP FIN. Not a RST/drop (ping-timeout would block a Fave ~30s later). The bouncer’s Rizon link stays up. A second fave is a new client attach.

**Allow insecure TLS** toggle (default **off**, DataStore, not a secret): bouncer IRC only, so a self-signed cert works. Label it as insecure. Do not offer this for r-a-d.io HTTP or `irc.rizon.net`.

**TLS fingerprint** (DataStore, not a secret): SHA-256 of the server certificate DER, colon-hex. Empty → any certificate that otherwise verifies (or insecure). Nonempty → every IRC connect (fave and Test connection) must present that exact cert; compare hex case-insensitively, ignore colons/spaces. **Test connection** reports the bouncer’s fingerprint as `SHA-256 …` so it can be pasted into this field. Client TLS session resumption is off so each connect sends the same client certificate (CertFP). A **client cert SHA-256** (from the PEM, first `CERTIFICATE`) is shown under the cert field and may be copied.

### SASL (optional)

Either, both, or neither. Client cert PEM nonempty → **EXTERNAL** (preferred, even if a SASL password is also set). Else password nonempty → **PLAIN**. Do not send PLAIN while a client cert is offered: many bouncers reject PLAIN when they see a TLS client certificate. The cert is always offered as mTLS when present. Clear the PEM to use SASL password.

1. If SASL is configured, `CAP LS 302` after optional `PASS`, then `NICK` / `USER`.
2. `CAP REQ :sasl` only if `sasl` is in `LS`. Otherwise fail visibly; do not `PRIVMSG` Hanyuu.
3. **PLAIN:** `AUTHENTICATE PLAIN` then the base64 of `\0username\0password`. Username is the SASL username field, or the public nick if that field is empty. Never log the token.
4. **EXTERNAL:** `AUTHENTICATE EXTERNAL` then `AUTHENTICATE +`. Requires a parseable client cert+key PEM.
5. `903` → `CAP END`. `904`/`905`/`902`/`906`/`907` → fail. Invalid PEM → fail before connect.

Rizon NickServ `IDENTIFY` is only when SASL was not used. Bouncer still never sends NickServ.

Client cert PEM may be one blob (`CERTIFICATE` + `PRIVATE KEY`) or cert + separate key field.

### Secrets

NickServ password, bouncer `PASS`, SASL password, client cert PEM, client key PEM: EncryptedSharedPreferences (Tink). Never log them, never log `IDENTIFY` / `PASS` / `AUTHENTICATE` payloads. Nick, host, port, profile, insecure-TLS flag, SASL username, TLS fingerprint: DataStore. UniFFI takes secrets in memory for the call only.

Password fields and the client **key** PEM do not offer copy or cut (paste-in is allowed so a value can be entered). Client **cert** PEM may be copied. Each PEM field has **Clear** above it; confirm before wiping the stored value.

### Tests

Local only: `IrcIo` scripts and a localhost TLS listener that speaks 001 / PING / Hanyuu NOTICE. Do not open `irc.rizon.net` or r-a-d.io in CI. Cover AFK `.fave {id}`, AFK `.unfave {id}`, live-DJ last/Added-only unfave, bouncer no-QUIT / no-NICK-after-001 / orderly close, self-signed fails unless the toggle is on, `001`/`NICK` rename (`nick_`) does not fave, SASL PLAIN payload, SASL EXTERNAL, PEM prefers EXTERNAL over PLAIN, SASL missing from `CAP LS` does not fave. A **Test connection** control on Settings → Connection runs handshake only (no `.fave`); it uses the on-device secrets and must not log them. Success includes the server certificate SHA-256. TCP tries every resolved address, **IPv6 first, IPv4 if that fails**. Host may include `:port`; that hostname is used for TLS SNI. Cover fingerprint pin (empty accepts; mismatch fails even with insecure TLS).

## UI

Search results + favorites can request when `requestable` / AFK. Show server error strings. Cooldown from can-request + snapshot. Faves JSON has no `requestable`; gray the Request button like search using the station delay (`requestcount` vs `lastplayed` / `lastrequested`, now = snapshot `current`).

**Request random** (Favorites tab): one tap picks uniformly among catalog favorites (`tracks_id` present) that pass that same cooldown, across **all** pages (GET live on tap). Then the same `POST /request/{id}`. Disable when nick is empty or requests are off. If none are requestable, show that — do not POST. Not on Auto.

Search and favorites paginate like news: **no list scroll**. Fill the pane with as many rows as fit in **normal viewing** (phone portrait, or the tablet two-pane; rotation must not change the count). UI pages slice a flat index over the server pages (search 20 / faves 100), stitching the next server page when a window crosses a boundary so only the true last leftover is short. Bottom bar, last page > 1: previous `<` and next `>` stay pinned at the bar edges; first page, a sliding window of nearby pages, `...` (jump-to-page), last page sit in the middle. Page numbers use a width for the last page’s digit count so 9→10 does not shift prev/next — measure that slot from the pager typeface (widest digit × count), not a dp-per-character guess. Jump-to-page accepts at most that many digits. Search `last_page` / `total` are in the JSON. Faves last page: HTML pagination (above), leftover-trimmed against the previous JSON page.

Process RAM holds fetched server pages for the current query/nick. Show RAM first. **UI page 1 always re-GETs** the covering server page(s) after that. Other UI pages GET only on a RAM miss. Leaving Request/Favorites in the same process keeps RAM; a successful Fave or unfave drops that nick’s RAM and re-GETs. Returning restores the last query/nick and UI page immediately from RAM, then follows the GET rules above. Overlay still applies on GET (new catalog fave at the top of server page 1; unfave removed from the current server page).

Favorites tab keeps the public nick field (list + IRC `NICK`). IRC connection (Rizon vs Bouncer, NickServ, host/port/`PASS`, Allow insecure TLS, SASL username/password, client cert/key PEM) lives on **Settings → Connection**, not this tab.
