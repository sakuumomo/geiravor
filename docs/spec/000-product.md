# 000 — Product

## Goal

Geiravor is the Android client for [r/a/dio](https://r-a-d.io): a live Icecast anime/Japanese music station. User-facing name: **r/a/dio**. Package: `io.r_a_d.geiravor`. It does not replace the existing Play listing.

## Success (0.1.0)

A listener can play and stop the live stream on a phone (minSdk 26) and from Android Auto, see current song / DJ / last played / upcoming queue, and have metadata follow the notification and lockscreen. The phone UI reads as the live **default-dark** site, not as Material purple or as the old orange Android app.

## Non-goals (unless a later spec says otherwise)

- Porting or copying any previous Android app
- WebView of r-a-d.io
- Consuming `/v1/sse` (HTML/htmx for the website)
- In-app IRC
- Android Automotive OS APK
- Scrobbler-specific code (Media3 session is enough for third-party scrobblers)
- Telemetry
- Documenting a non-Nix Android Studio install as the supported flow

## Semver

`MAJOR.MINOR.PATCH` in Android `versionName`, Cargo `version`, and User-Agent `Geiravor/X.Y.Z`. Those three strings are always identical.

| Version | Meaning |
|---|---|
| **0.1.0** | First ship: live listener + Android Auto core. `versionCode` 1. |
| **0.1.x** | Fixes only. |
| **0.2.0** | Search, request, nick faves, news, alarm/snooze, sleep timer, DJ-online notifier. |
| **0.3.0+** | Further site-parity (schedule, staff, submit, extra themes, …) while still pre-1.0. |
| **1.0.0** | Not a feature dump. The maintainer runs the app, finds it satisfactory, and we bump. May follow 0.1.0 or a later 0.x. |
| After 1.0.0 | Breaking → MAJOR, features → MINOR, fixes → PATCH. |

`versionCode` is a separate integer: start at 1, add 1 on every tagged release. Do not encode X.Y.Z into it.

While `MAJOR == 0`, MINOR may add features; 0.x is not API-stable.

## 0.1.0 scope

- Play/stop `https://stream.r-a-d.io/main.mp3` (HTTPS `audio/mpeg`). No seek, no skip. Pause control **stops**.
- Now playing from `GET https://r-a-d.io/api`: split `np`, collapsible tags, unix-second progress (AFK only), listeners, `dj.djname`, DJ image, thread on phone only, previous (`lp[0]`) and next (`queue[0]`, or `???` for a live DJ).
- Songs tab: last played (5) and queue (5) from `timestamp`, relative times. Hide queue when `isafkstream == false`. Mark `type == 1` as `/r/`. Bottom tabs (Now Playing \| Songs \| Settings). Songs/Settings use section tabs; two-pane of those sections only at ≥ 840 dp.
- Volume 0–100, default 80, on now-playing.
- Media notification, lockscreen, Bluetooth. Unplug and audio-focus loss stop playback. Optional auto-start on plug and in vehicle (independent, both default off).
- Android Auto: play/stop, metadata, prev/next **text**, browse Last Played and (when AFK) Queue.
- Stream-down / offline as a state (player/HTTP error — not a magic title).
- Process-wide `/api` poller.

## 0.2.0 scope

Search + request, nick-only public faves (no login), news, alarm/snooze + fallback sound, sleep timer, opt-in DJ notifier via WorkManager.

## Later

Everything user-facing on the site: schedule, staff, submit, help, extra themes.

## Identity

Nick-only public favorites. No password, no site session login.

## Identity of the app

- User-Agent: `Geiravor/X.Y.Z`
- Launcher label: `r/a/dio`
- License: MIT (`LICENSE.md`), copyright Sakurai Momoka
