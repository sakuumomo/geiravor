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
- Auto now-playing is title, artist, and DJ (no `| DJ` pipe). The shade still ellipsizes `artist | DJ`. Tapping the notification opens the app. AFK songs are not reported as live.
- Schedule Friday no longer steals Saturday’s DJ image; schedule portraits fit in the row. Hug panes sit at the top. Pager uses one jump `…` and keeps two-digit page numbers on one line. News keeps block spacing and full-width images. Connection drops the “Rizon vs bouncer” heading, labels the bouncer secret **Server password**, and treats an empty port as 6697. Alerts use compact time/duration rows. Next / Last played are labeled and centered. The media notification stays up while idle so Play still has a shade.
- Favorites Request uses the station delay (`requestcount` vs last played / last requested). Shade, Auto, and DJ-online notices use Coil stills (GIF first frame); mystery-DJ only on artwork miss. Article **← News** sits on a theme film chip with hover.
- IRC fave handshake waits for SASL and `001` together, `AUTHENTICATE +`, NickServ after welcome, host `:port` for SNI, and TCP FIN after `close_notify`. Membership is a full-nick overlay (not the visible window); heart checks both committed nicks. HTTP gzip; sqlite WAL.
- Hug panes (Now Playing, Last Played, Queue, schedule, staff) wrap their height and sit at the **top** of the tab. News article TextView drops extra font padding; changing alarm time reschedules.
- Schedule portraits come from that day’s cell (image-left or image-right); Friday keeps an empty image slot. News body/comments no longer leak CSS class text. Alarm clock is AM/PM or 24-hour. Glass frost copies the laid-out wallpaper via `positionInRoot`.
- Play enters FGS immediately with a media shade (DJ still, play/stop, mute, Fave, Vol − / Vol +) so Icecast buffering no longer trips the foreground-service timeout. After stop the shade is not ongoing; swipe dismisses it and it stays gone until Play. Mute sits next to volume on Now Playing. Listener count is labeled. Inner blocks use a 1.dp border and pad (not on Now Playing). Favorites nick lives only on the Favorites tab. Quote `#id` puts a space before `>>` when the composer has no trailing newline. ← News film is transparent until hover. Schedule today highlights the row border. Extra pad between a news article and its comments. Quick fave/unfave taps queue up to three in the in-flight window; extras are ignored. Sleep is cancelled only when play is actually stopped, not on a buffer blip. Unselected pager chips use the raised border fill.
- Auto/focus pause drops FGS and posts a dismissible Play shade. Domain `playing` follows want-play so buffer does not show Play or fire fave-on-air. Swipe-dismissed shade stays gone until the next Play or alarm. Sleep off cancels the timer without stopping Icecast; stop turns the sleep toggle off. Alarm fire reschedules tomorrow; the ringing notice is cancelled on Stop, Snooze, and live start. FIRE/SNOOZE are not exported. Cold Auto paints the heart from disk membership. Auto-start in vehicle is projected Auto only. Poller GETs `/api` immediately, then sleeps. Denied DJ/fave notification permission is fail-visible.
- Favorites nick and Request search sit above the list with a 12.dp gap. ← News film is partial at rest and opaque on hover. Mid-line `#id` quotes insert ` >>id ` (space after, no newline). A paused shade is posted once and is not rewritten or cancelled by `/api` polls.
