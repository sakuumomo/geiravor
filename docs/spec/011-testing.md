# 011 — Testing

## Rust

Committed fixtures under `core/tests/fixtures/` captured from `https://r-a-d.io/api` (never from a live `.mp3`).

Must cover:

- JSON parse of `main` (ignore HTML `time`)
- `np` split (including no-hyphen and extra `" - "` in the title)
- Progress offset with unix **seconds**
- Queue hidden when `isafkstream == false`
- Live DJ `tags: null` parses as empty (must not fail the snapshot)
- Live DJ ignores catalog `start_time` / `end_time`
- `end_time == 0` unknown duration (AFK)
- Poll interval floor 2s / backoff
- ICY change triggers refetch and does not overwrite `np`

`cargo test` on the host; no NDK. Do not open `r-a-d.io`, `irc.rizon.net`, or Icecast from unit tests. Emulate online behavior locally: JSON fixtures for HTTP; `IrcIo` scripts plus a localhost TLS listener that speaks enough IRC (001, PING, Hanyuu NOTICE) for add-fave (`006-requests-faves.md`). Self-signed bouncer cert fails unless **Allow insecure TLS** is on. SASL PLAIN/EXTERNAL are scripted locally; never log AUTHENTICATE payloads.

0.2.0 fixtures also cover search, can-request (`Main`), request CSRF (`gorilla.csrf.Token` HTML snippet, not a live homepage scrape), `/faves`, news.

0.3.0 fixtures cover `GET /schedule` and `GET /staff` HTML (committed, not live), theme-name from `/assets/{name}/css/`, schedule local-time rewrite, holiday-window and sniff decision tables.

## Kotlin

Fake UniFFI core. Cover pause→stop (drop live buffer, no reconnect while stopped), idle notification kept after stop, playlist kept on stop, Play after stop is not swallowed, notification permission path (compat; play does not require it), Auto root Songs+Settings, Queue folder hidden when not AFK, settings toggle rows, auto-start on plug and in vehicle default off (independent), Songs Queue section hidden when not AFK, volume ±5 for Auto, `COMMAND_GET_TIMELINE` removed (no Queue chrome), live `MediaItem` carries split `np` / DJ artwork. Two-pane at ≥840dp **and** smallest width ≥ 600dp.

0.2.0: bottom tabs Now Playing \| Songs \| News \| Settings (phone) and Songs \| News \| Settings (two-pane right); Songs sections Last Played \| Queue \| Request \| Favorites; no UI string **Faves**; request disabled when not AFK; empty nick Fave no-op; Auto Fave does not `setMediaItem`; Auto connect shows paused now-playing without a Settings tap or Play; cold Auto Songs includes Queue when last paint was AFK; auto-start off does not play on connect; live metadata updates do not restart Icecast; live Next is `???`; live DJ hides the phone progress bar; thread hidden on Hanyuu; `image:` embeds; mute icon is not vol +; last snapshot paint does not restore stream-down; alarm compat policy; Settings sections General \| Auto \| Connection \| Alerts; alarm denied copy is visible; snooze actions labeled Stop/Snooze; snooze and sleep durations are custom hours+minutes (not a preset cycle); sleep timer fade is the last 15s and does not persist gain; manual stop cancels an armed sleep timer; DJ notifier default off; AFK→live notifies; live→AFK does not; Hanyuu (AFK stream) does not notify even if `isafkstream` is false; first sample does not; battery copy is visible; Request random picks a cooldown-ok catalog favorite and uses the same POST as row Request; peek other nick is HTTP only until IME Done or a successful `/faves` GET (last successful Favorites nick, not the first nonempty); home-nick membership is Room + RAM; committed faves listing pages are Room + RAM (cache first, first+last server pages so leftover last is not padded to 100); PEM Clear confirm copy; fingerprint compare ignores colons/case; empty pin accepts; news list is HTML `/news?page=` not the 3-item `/api/news` snapshot; news list pages fit the pane (no list scroll, catalog-flat leftover only at the true end); news/search/faves visible count is how many rows fit the current pane without scrolling; membership disk is committed Favorites nick and Connection nick only; search/faves RAM page cache; news disk+RAM catalog show-cache-first; viewed news article bodies stay on disk; staff/dj/dev name colors; news header is list-only; article **← News** stays pinned as a link over the scrolling body, not a toolbar; news `<time data-type=local>` is device-local; `#id` quotes `>>id\\n`; `href="#comment-{id}"` jumps; comments HTML GET/POST `/news/{id}` max 500.

0.3.0: bottom tabs Now Playing \| Songs \| **Board** \| Settings (phone) and Songs \| Board \| Settings (two-pane right); Board sections News \| Schedule \| Staff (default News); last favorites JSON page is leftover only, not filled from the previous page; schedule HTML `/schedule` Monday-first, empty day is not Hanyuu, today is device-local weekday; local-time rewrite default off, unambiguous tokens only (`8pm`, `8 pm`, `2PM`, `22:00`, `11:59 pm EST`), leave unmatched text; staff HTML `/staff` groups staff \| dev \| dj, no bio; Staff\|Developers pair from 600dp; DJs 4-wide / 2-wide phone; glass holiday highlight is lifted from `--edenlight-color`; holiday list pages sit on a pane; glass scrim stays readable on light wallpaper; DiskPolicy: equal payload is not written; holiday windows 29 Oct–1 Nov / 1–26 Dec / 27 Dec–3 Jan; opt-out default off; sniff not outside windows; sniff at most hourly until holiday seen on; theme packs default-dark / default-light / christmas / halloween / newyears are all user-selectable; opt-out blocks auto only; Auto has no schedule/staff/news and follows dark/light pick (holiday user pick or holiday auto → Default / night); application night mode is Default light → day else night; changing theme stays on Settings; no Icecast GET; committed HTML fixtures, no live `/api` in CI.

## CI

`nix develop -c cargo test --manifest-path core/Cargo.toml`, `nix develop -c ./gradlew :app:testDebugUnitTest`, and `nix develop -c ./gradlew :app:assembleDebug`. No emulator. Do not GET the Icecast URL, live `/api`, or Rizon. DHU FHS wrap is Linux-only.

## Manual (0.1.0)

Phone: play/stop, volume, unplug, notification play/pause (stays after pause; play even if permission denied), rotation, airplane, audio-focus interruption, AFK vs live DJ next `???` / queue hide, Settings auto-start on plug / in vehicle (both default off) and version. Tablet (width ≥ 840dp and smallest width ≥ 600dp): Now Playing stays left; Songs/Settings switch the right pane. 16:9 phone landscape stays single-pane.

DHU: keep `nix develop -c ./scripts/dhu.sh` (Waydroid + Android Auto Head Unit Server) as the Auto test rig. App appears, play/stop, no skip buttons, no Queue chrome, title plus artist unfocused / title-artist-DJ focused, Mute and Fave on unfocused now-playing, Vol − / Vol + on focused, AFK progress bar on unfocused and focused now-playing (none for a live DJ), Songs and Settings tabs, browse does not change audio. Pause/focus-loss drops the Icecast GET. **Not a merge gate.** Phone and real-car work do not wait on DHU. CI does not run DHU.

Projected Android Auto has been run in a real car for 0.1.0. That does not cover every OEM skin.
