# 001 — Architecture

## Split

Rust owns domain, HTTP to `/api`, JSON, poll policy, now-playing reducer. Kotlin owns Compose UI, Media3, Android Auto, notifications, DataStore, WorkManager/AlarmManager.

**Not in Rust:** ExoPlayer, Auto templates, Compose, notifications, alarms.

**Not in Kotlin (0.1.0):** `/api` JSON parse, `np` split, progress math, poll interval.

**Not in Kotlin (0.2.0):** search/faves HTTP, JSON (and faves last-page HTML extract), IRC add-fave. Home-nick membership RAM lives in `RadioCore`; Room persistence is Kotlin.

## Layout

- `core/` — Rust crate: `rlib` + `cdylib`, UniFFI. No Android types.
- `app/` — one Gradle module, one `Activity`, package `io.r_a_d.geiravor`.
- `app/.../compat/` — API-level shims (`009-compat.md`).
- Root `Cargo.toml` workspace member `core`, `version = "0.1.0"`.
- Root Gradle wrapper committed.

## Process

`Application.onCreate` initializes UniFFI and starts a **process-wide** poller. `MediaLibraryService` and the UI share that store. Auto and the notification must update with the Activity dead.

UniFFI callbacks arrive off the main thread. Kotlin hops to Main before Compose or `MediaSession`.

`core` polls on a **dedicated `std::thread`** with **blocking** `reqwest`. UniFFI public API is sync + callbacks, not UniFFI-async. Do not add Tokio unless a later spec needs concurrent 0.2.0 HTTP.

## HTTP (Rust)

- `reqwest` + `rustls-tls` + `webpki-roots` (`blocking` + `rustls-tls-webpki-roots`)
- No OpenSSL
- No `rustls-platform-verifier` in 0.1.0
- User-Agent `Geiravor/X.Y.Z` (same as `versionName`)
- Trait `ApiClient` (`get` today). 0.2.0 adds `search` / `can_request` / `request` / `faves` / `news` on this trait (`006-requests-faves.md`, `007-news.md`). Cookie jar on the same blocking `reqwest` client (`_gorilla_csrf` + `X-CSRF-Token` on POST). **No** HTTP `fave_add` — add-fave is IRC (`006-requests-faves.md`).

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
