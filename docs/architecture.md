# Architecture

Repo contract. The site does not override this file. Listener behavior is [`spec/`](spec/).

## Split

One `core` crate (modules + traits). Parsers must **not** depend on reqwest. No policy crate — policy sits next to the behavior. Split a crate later only if a dep or ownership boundary hurts.

**Rust:** HTTP, JSON/HTML parse, poll, now-playing reducer, IRC fave, sqlite catalogs + last-paint + non-secret settings, membership, theme **decision**, in-flight coalescer.

**Kotlin floor:** Compose, Activity/Manifest, Media3 / Auto session, notifications, FGS, WorkManager, AlarmManager, Keystore (IRC secrets), Coil (phone GIF; shade/Auto stills), `compat/`.

Kotlin **never** calls network or sqlite UniFFI on the Android main thread. UniFFI is **sync + callbacks**, not UniFFI-async. Callbacks arrive off main; hop to Main before Compose or `MediaSession`.

Theme **tokens/paint** stay in Compose. Coil keeps DJ/staff/thread images (URL disk cache), not sqlite blobs.

## Runtime

**No Tokio.** Named **poller thread** (process-wide `/api`, blocking `reqwest` + rustls, no OpenSSL) plus a **worker pool** (search, HTML pages, faves, IRC). Never the poller unless queued. **In-flight coalescer:** one GET per URL. Escape hatch if IRC/connect racing stays messy.

`Application.onCreate` initializes UniFFI, passes **`filesDir`** (Rust does not guess storage paths), restores last-paint into the domain so Auto/fave have a snapshot before the first poll, and starts the poller. `MediaLibraryService` and the UI share that snapshot. Auto and the notification must update with the Activity dead. Cold Auto hydrates committed-nick membership from disk, then `/faves`, when the session starts.

Sqlite is behind **one mutex** (poller, workers, UI reads).

HTTP: `reqwest` blocking + `rustls-tls-webpki-roots`. Cookie jar on that client (`_gorilla_csrf` + `X-CSRF-Token` on POST). User-Agent `Geiravor/X.Y.Z`. HTTP client is get / post-csrf / post-form only. **No** HTTP add-fave — IRC is a separate module (`std::net` + rustls, no IRC crate).

Transport/parse failures map to a small error type `{ Network, Http, Decode }`. Stream-down is a **player** state ([spec/playback.md](spec/playback.md)), not an `/api` miss.

HTML/JSON parse is **bounded** (size cap).

## Disk

Show cache first. GET. **Write only if the new payload is actually different.** If it is different, update the screen. If it is the same, do not write and do not flash loading. One equality policy for:

- News list pages / article body / comments
- Schedule week
- Staff list
- Home-nick membership
- Committed faves listing pages
- Last-paint (skip write when chrome is unchanged; `current` and `listeners` tick every poll)

Search listing stays off disk (process RAM). Peeks stay HTTP + RAM. News, schedule, and staff share the HTML-page pipeline, not one Composable or one SQL table. Theme name is parsed from HTML already in hand (`/assets/{name}/css/`).

## UniFFI on Android

- `cargo ndk` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, `--platform 26`
- Output into `app/src/main/jniLibs/` (gitignored)
- Depend on `net.java.dev.jna:jna` **AAR**
- R8 keep rules for `uniffi` and JNA
- Host `cargo test` does not need the NDK

## Logging

`tracing` → Android logcat, tag `geiravor`. Filter: `adb logcat -s geiravor:*` on the Waydroid serial.

| Level | When |
|---|---|
| ERROR | Listener-facing failure |
| WARN | Recoverable retry |
| INFO | Lifecycle (play/stop, session start, fave ok/fail). **Not** every 2s poll |
| DEBUG | URLs (no secrets), cache, poll interval |
| TRACE | Off by default |

Never log NickServ / SASL / PASS / PEM / CSRF **values** / cookies. Debug APK = DEBUG, release = INFO. No in-app viewer, no file log, no telemetry.

## Layout

- `core/` — domain crate. No Android types.
- `app/` — one Gradle module, one Activity, package `io.r_a_d.geiravor`
- `app/.../compat/` — [compat.md](compat.md)
- Root `Cargo.toml` workspace member `core`

## Agents

Default: one agent. Planes: docs, domain, shell, tooling. Fan-out only with a frozen UniFFI contract. See [`AGENTS.md`](../AGENTS.md).
