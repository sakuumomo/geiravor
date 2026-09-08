# 010 — Alarm, sleep, DJ notifier (0.2.0)

Not built in 0.1.0.

## Alarm

Exact alarm via `AlarmManager` + compat (`009-compat.md`). On fire: start the live stream through the same play path as 0.1.0 (`003-playback.md`) — pause still **stops**. If the stream cannot start (no network / player error), play a **committed local** fallback sound in `res/raw` — not a network asset at ring time. Do not GET Icecast in tests.

Snooze: optional disable; duration is a custom hours + minutes (1 minute–12 hours, default 10 minutes). Notification while ringing uses large labeled actions (not icon-only).

## Sleep timer

One-shot timer to stop playback through the same play/stop path (`003-playback.md`). Settings → Alerts: toggle (default **off**) and a custom hours + minutes duration (1 minute–12 hours, default **30** minutes). Arming stores a deadline in DataStore. The last **15 seconds** fade player gain to 0; do **not** persist the faded gain. Then stop (pause still **stops**). Manual stop (pause/stop, unplug, audio-focus loss) cancels the timer. Changing duration while armed restarts the deadline. Not on Auto. Do not GET Icecast in tests.

## DJ-online notifier

Opt-in, default **off**. **WorkManager** periodic (15 minutes, network required) plus the process `/api` poller watching snapshot `dj` / `isafkstream`. Do **not** use a `specialUse` foreground service to poll. Notify on AFK → live DJ, live DJ identity change, and live DJ → Hanyuu / AFK. Hanyuu (`isafkstream` or `dj.djname` Hanyuu / Hanyuu-sama) is the AFK stream even if `isafkstream` lags a takeover — AFK → Hanyuu is not a notify. Do not notify on the first sample after opt-in, or while stream-down (no snapshot / neither connected; the next good sample can fire). Song / `np` changes are not a notify. Tap opens the app. Title is **`r/a/dio`**. Body for a live DJ is **`{dj.djname} is LIVE`** (`LIVE` in capitals; empty name → `A DJ is LIVE`). Body returning to AFK is **`{dj.djname} is back`** (empty → `Hanyuu-sama is back`). Large icon is the DJ image still from the Coil cache (GIF first frame); omit it on miss.

No push channel exists from the station. Settings → Alerts documents battery cost. If notification permission is denied, fail visible with Settings copy — not a silent skip. Do not GET Icecast.

## Fave currently playing

Opt-in, default **off**. Same WorkManager (15 minutes, network required) plus the process `/api` poller as the DJ notifier — one worker if either toggle is on; both off cancels it. Do **not** use a `specialUse` foreground service to poll.

Notify when the on-air song becomes a **favorite** of a committed nick (`FavePolicy.isMember`: Favorites nick and Connection nick). AFK uses catalog `trackid`; live DJ matches `np` (never leftover AFK `trackid`). Empty nick is a no-op. Do not notify on the first sample after opt-in, while stream-down, or on the same track again. **Suppress while this app is playing the stream** (the media notification already shows the song); still record the sample so pause later on that same track does not fire. Title is **`r/a/dio`**. Body is the on-air `np` (empty → `A favorite is playing`). Tap opens the app. Not on Auto.

Settings → Alerts documents that this shares the DJ check and does not notify while playing. Permission denied is visible — not a silent skip. Do not GET Icecast.
