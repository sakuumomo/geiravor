# Product

Geiravor is the Android client for [r/a/dio](https://r-a-d.io): a live Icecast anime/Japanese music station. User-facing name: **r/a/dio**. Package: `io.r_a_d.geiravor`. It does not replace the existing Play listing.

## Success

A listener can play and stop the live stream on a phone (minSdk 26) and from Android Auto; see now playing, last played, and queue; search and request on AFK; list and add favorites under a Rizon nick; read news, schedule, and staff; set alarm and sleep; opt into DJ-online and fave-on-air notices; pick Default, Default light, Christmas, Halloween, or New Years, and get holiday palettes automatically when the live site is serving them — without a site login and without an IRC client. Metadata follows the notification and lockscreen. The phone UI reads as the live site, not Material purple and not the old orange Android app.

## Non-goals

- Porting or copying any previous Android app
- WebView of r-a-d.io
- Consuming `/v1/sse` (HTML/htmx for the website)
- A general-purpose IRC client, channel UI, chat log, or native Quassel. The only IRC is a **short-lived TLS session** to `PRIVMSG` `Hanyuu-sama` (`.fave` / `.fave last` / `.fave <id>` / matching `.unfave`) then leave
- Android Automotive OS APK
- Scrobbler-specific code (Media3 session is enough for third-party scrobblers)
- Telemetry
- Documenting a non-Nix Android Studio install as the supported flow
- Submit, help, remaining public themes (suzu, TuiCSS, Eden Light), DJ CSS (`dj.theme_id` / `dj.css`)

## Identity

No r-a-d.io **site** session or password. Public favorites list is nick-only (`GET /faves?nick=`).

Add-fave is IRC. Direct Rizon uses that nick as `NICK` plus optional NickServ password or SASL. A generic bouncer (ZNC/soju) is already named and identified on Rizon; the stored nick must be that same Rizon nick so the list matches what Hanyuu records. NickServ, bouncer `PASS`, SASL password, and client PEM live in encrypted storage and are never logged. Password and client-key fields are not copyable; the client cert may be. Optional server-cert SHA-256 pin; Test connection reports the bouncer’s fingerprint.

Empty nick = fave no-op and empty list.

## Identity of the app

- User-Agent: `Geiravor/X.Y.Z` (same string as Android `versionName` and Cargo `version`)
- Launcher label: `r/a/dio`
- License: MIT (`LICENSE.md`), copyright Sakurai Momoka

How those strings are bumped is repo documentation (`docs/build.md` when written). App versions are **semver**, not the site’s `/v1/` path.
