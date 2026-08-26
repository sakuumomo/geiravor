# 006 — Requests and favorites (0.2.0)

Not built in 0.1.0. Specced so `ApiClient` (`core`, `get` for `/api` today) can grow without a rewrite.

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

Production uses **gorilla csrf**: cookie `_gorilla_csrf` (HttpOnly; Secure; SameSite=Lax; 7 days) plus header `X-CSRF-Token`, base62. A compatibility shim also accepts JSON `{ "_token": "..." }`. CSRF is **not** skipped on `/request`.

There is no JSON token endpoint. HTML scrape of `<form` is fragile. **0.2.0 starts with a spike:** cookie jar + how to obtain a token from a cheap GET. Do not copy an old app’s scraper as architecture.

JSON error/success keys from the legacy POST: `success` or `error` strings (song cooldown, user cooldown, requests disabled).

## Favorites

No login. User types their public nick.

`GET https://r-a-d.io/faves?nick=&page=&dl=true` → array of `{ tracks_id, meta, lastrequested, lastplayed, requestcount }` with nulls. `meta` is `"Artist - Title"`. Page size 100. Unknown nick → `[]`, not an error.

Persist nick in DataStore.

## UI

Search results + faves can request when `requestable` / AFK. Show server error strings. Cooldown from can-request + snapshot.
