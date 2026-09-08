# Changelog

User-visible changes to **this** tree. 0.3.0 history lives on `legacy/0.3`.

## [Unreleased]

- Kotlin floor: Compose Play/Stop, Media3 live session (pause stops Icecast), Auto browse Songs + Settings, EncryptedSharedPreferences for IRC secrets, `compat/` shims, Coil GIF loader.
- Phone chrome: Now Playing | Songs | Board | Settings, five committed theme packs, snapshot-backed now playing / last played / queue, Settings prefs.
- Board (news / schedule / staff), Request search, and Favorites list parse in `core/` from live HTML/JSON.
- Pause/stop keeps the session paused `STATE_READY` (Icecast still torn down). `scripts/dhu.sh` discovers the adb serial.
- Alarm, sleep timer, DJ-online and fave-on-air notices; ICY refetch; auto-start on plug/vehicle; schedule local times; logcat tag `geiravor`.
- News HTML + Coil images, `>>id` jump, local `data-dur` times, and role colors; shade ellipsize keeps `| DJ`; pager with `…` jump; Test connection; PEM Clear confirm; holiday frost aligned to the page wallpaper.
- News, Request, and Favorites fill the pane by stitching server pages so only the true last leftover is short.
- Thread embed long-press save/open (phone only); compact Now Playing logo badge; theme-radio hover; list row count locked across rotation.
- Holiday theme sniff reuses news/schedule/staff HTML, GETs `/` at most hourly until the live pack matches, and Board section labels come from domain constants.
- GIF and muted looping video autoplay on DJ, schedule, staff, and thread; news article images stay still.
- Coil drops news image files when those URLs leave the article body or comments.
- Fave failure text fades out when `np` changes instead of vanishing.
- Live stream auto-reconnects after 2s on error/end while play is wanted; snooze and sleep take hours + minutes (1 min–12 h); sleep duration change restarts the timer; alarm notification uses labeled Stop/Snooze actions.
- Android Auto Settings lists auto-start in vehicle and on plug (On/Off) plus About; tapping a flag flips it without starting the stream. Mute / Fave / Vol − / Vol + sit in media-button slots; the Fave heart matches the phone.
- Staff and Developers sit side by side from 600dp; DJs are four-across there and two-across on phones; leftover cards stay together; group titles are centered with a 2dp rule.
