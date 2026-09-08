# 001 — Architecture

## Split

Rust owns domain, HTTP to `/api`, JSON, poll policy, now-playing reducer. Kotlin owns Compose UI, Media3, Android Auto, notifications, DataStore, WorkManager/AlarmManager.

**Not in Rust:** ExoPlayer, Auto templates, Compose, notifications, alarms.

**Not in Kotlin (0.1.0):** `/api` JSON parse, `np` split, progress math, poll interval.

**Not in Kotlin (0.2.0):** search/faves HTTP, JSON (and faves last-page HTML extract), IRC add-fave. Home-nick membership RAM lives in `RadioCore`; Room persistence is Kotlin.

**Not in Kotlin (0.3.0):** schedule/staff HTML parse, theme name from `/assets/{name}/`. Room persistence, DiskPolicy, Compose, DataStore, Coil stay Kotlin.

## Layout

- `core/` — Rust crate: `rlib` + `cdylib`, UniFFI. No Android types.
- `app/` — one Gradle module, one `Activity`, package `io.r_a_d.geiravor`.
- `app/.../compat/` — API-level shims (`009-compat.md`).
- Root `Cargo.toml` workspace member `core`, `version = "0.3.0"`.
- Root Gradle wrapper committed.

## Process

`Application.onCreate` initializes UniFFI and starts a **process-wide** poller. Last paint restores into both the Kotlin UI store and `RadioCore` so Auto/fave have a snapshot before the first poll. `MediaLibraryService` and the UI share that store. Auto and the notification must update with the Activity dead. Cold Auto loads committed-nick membership (Room, then `/faves`) when the session starts.

UniFFI callbacks arrive off the main thread. Kotlin hops to Main before Compose or `MediaSession`.

`core` polls on a **dedicated `std::thread`** with **blocking** `reqwest`. UniFFI public API is sync + callbacks, not UniFFI-async. Do not add Tokio unless a later spec needs concurrent 0.2.0 HTTP.

## HTTP (Rust)

- `reqwest` + `rustls-tls` + `webpki-roots` (`blocking` + `rustls-tls-webpki-roots`)
- No OpenSSL
- No `rustls-platform-verifier` in 0.1.0
- User-Agent `Geiravor/X.Y.Z` (same as `versionName`)
- Trait `ApiClient` is HTTP only (`get` / `post_csrf` / `post_form`). Search, news, schedule, staff, and theme-name parse are **RadioCore** methods on that client (`006-requests-faves.md`, `007-news.md`, `013-schedule-staff.md`). Cookie jar on the same blocking `reqwest` client (`_gorilla_csrf` + `X-CSRF-Token` on POST). **No** HTTP `fave_add` — add-fave is IRC (`006-requests-faves.md`).

## UniFFI on Android

- `cargo ndk` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, `--platform 26`
- Output into `app/src/main/jniLibs/` (gitignored)
- Depend on `net.java.dev.jna:jna` **AAR**
- R8 keep rules for `uniffi` and JNA
- Host `cargo test` does not need the NDK

## Errors

Rust maps transport/parse failures to a small error type **`ApiError` { Network, Http, Decode }**. Stream-down is a **player** state (`003-playback.md`), not an `/api` parse miss: a stale snapshot with a dead player is still stream-down.

## 0.2.0

Search/request/faves-list/news add methods on `ApiClient` so 0.1.0 does not have to be rewritten. CSRF cookie jar is specified in `006-requests-faves.md` (request POST only). Do not add those methods until 0.2.0.

IRC fave is a **separate** Rust module (not `ApiClient`): blocking `std::net` + rustls, no Tokio, no IRC crate. Extra HTTP from UI runs on a worker — never the Android main thread, never the `/api` poller unless queued. After IRC `001`, sample the process-wide latest snapshot already in `RadioCore` (no extra GET) for the live-DJ fave machine.

## 0.3.0

Schedule/staff are RadioCore methods (same as news): GET via `ApiClient`, parse in `core/`. One HTML-page pipeline: GET → parse → show cache → write if `DiskPolicy.changed` → update screen. News, schedule, and staff share that pipeline, not one Composable or one SQL table. Theme name is parsed from HTML already in hand (`/assets/{name}/css/`).

### Disk

Show cache first. GET. **Write only if the new payload is actually different.** If it is different, update what is on screen. If it is the same, do not write and do not flash loading. One policy (`DiskPolicy.changed`) for:

- News list pages / article body / comments
- Schedule week
- Staff list
- Home-nick membership
- Committed faves listing pages
- Last-paint blob

Coil stays URL-keyed (DJ + staff images). Skip Room upserts and Coil rewrites when equal. Search listing stays off disk (process RAM of fetched server pages). **Committed** favorites listing pages (Favorites nick and Connection nick — one row when they are the same) stay on disk: show cache first, GET, write if `DiskPolicy.changed`, prune when those nicks change. Peeks stay HTTP + RAM. News list HTML pages and viewed article bodies stay on disk and are copied into RAM (`007-news.md`). Last-paint: skip write when chrome is unchanged (`current` and `listeners` tick every poll and must not force a rewrite).
