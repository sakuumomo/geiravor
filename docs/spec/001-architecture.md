# 001 — Architecture

## Split

Rust owns domain, HTTP to `/api`, JSON, poll policy, now-playing reducer. Kotlin owns Compose UI, Media3, Android Auto, notifications, DataStore, WorkManager/AlarmManager.

**Not in Rust:** ExoPlayer, Auto templates, Compose, notifications, alarms.

**Not in Kotlin (0.1.0):** `/api` JSON parse, `np` split, progress math, poll interval.

## Layout

- `core/` — Rust crate: `rlib` + `cdylib`, UniFFI. No Android types.
- `app/` — one Gradle module, one `Activity`, package `io.r_a_d.geiravor`.
- `app/.../compat/` — API-level shims (`009-compat.md`).
- Root `Cargo.toml` workspace member `core`, `version = "0.1.0"`.
- Root Gradle wrapper committed.

## Process

`Application.onCreate` initializes UniFFI and starts a **process-wide** poller. `MediaLibraryService` and the UI share that store. Auto and the notification must update with the Activity dead.

UniFFI callbacks arrive off the main thread. Kotlin hops to Main before Compose or `MediaSession`.

Tokio lives on a dedicated thread inside `core`. UniFFI public API is sync + callbacks, not UniFFI-async.

## HTTP (Rust)

- `reqwest` + `rustls-tls` + `webpki-roots`
- No OpenSSL
- No `rustls-platform-verifier` in 0.1.0
- User-Agent `Geiravor/X.Y.Z` (same as `versionName`)

## UniFFI on Android

- `cargo ndk` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, `--platform 26`
- Output into `app/src/main/jniLibs/` (gitignored)
- Depend on `net.java.dev.jna:jna` **AAR**
- R8 keep rules for `uniffi` and JNA
- Host `cargo test` does not need the NDK

## Errors

Rust maps transport/parse failures to a small error type (network, http, decode). Stream-down is a **player** state (`003-playback.md`), not an `/api` parse miss: a stale snapshot with a dead player is still stream-down.

## 0.2.0

Search/request/faves stay behind traits on the same `ApiClient` so 0.1.0 does not have to be rewritten. CSRF cookie jar is specified in `006-requests-faves.md`.
