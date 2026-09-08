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
