# 010 — Alarm, sleep, DJ notifier (0.2.0)

Not built in 0.1.0.

## Alarm

Exact alarm via `AlarmManager` + compat (`009-compat.md`). On fire: start the live stream through the same play path as 0.1.0 (`003-playback.md`) — pause still **stops**. If the stream cannot start (no network / player error), play a **committed local** fallback sound in `res/raw` — not a network asset at ring time. Do not GET Icecast in tests.

Snooze: optional disable; duration is a custom hours + minutes (1 minute–12 hours, default 10 minutes). Notification while ringing uses large labeled actions (not icon-only).

## Sleep timer

One-shot timer to stop playback through the same play/stop path (`003-playback.md`). Settings → Alerts: toggle (default **off**) and a custom hours + minutes duration (1 minute–12 hours, default **30** minutes). Arming stores a deadline in DataStore. The last **15 seconds** fade player gain to 0; do **not** persist the faded gain. Then stop (pause still **stops**). Manual stop (pause/stop, unplug, audio-focus loss) cancels the timer. Changing duration while armed restarts the deadline. Not on Auto. Do not GET Icecast in tests.

## DJ-online notifier

Opt-in, default **off**. **WorkManager** periodic (15 minutes, network required) plus the process `/api` poller watching snapshot `dj` / `isafkstream`. Do **not** use a `specialUse` foreground service to poll. Notify on AFK → live DJ, live DJ identity change, and live DJ → Hanyuu / AFK. Hanyuu (`isafkstream` or `dj.djname` Hanyuu / Hanyuu-sama) is the AFK stream even if `isafkstream` lags a takeover — AFK → Hanyuu is not a notify. Do not notify on the first sample after opt-in, or while stream-down (no snapshot / neither connected; the next good sample can fire). Song / `np` changes are not a notify. Tap opens the app. Title is **`r/a/dio`**. Body for a live DJ is **`{dj.djname} is LIVE`** (`LIVE` in capitals; empty name → `A DJ is LIVE`). Body returning to AFK is **`{dj.djname} is back`** (empty → `Hanyuu-sama is back`).

No push channel exists from the station. Settings → Alerts documents battery cost. If notification permission is denied, fail visible with Settings copy — not a silent skip. Do not GET Icecast.
