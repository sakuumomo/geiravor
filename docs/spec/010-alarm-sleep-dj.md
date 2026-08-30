# 010 — Alarm, sleep, DJ notifier (0.2.0)

Not built in 0.1.0.

## Alarm

Exact alarm via `AlarmManager` + compat (`009-compat.md`). On fire: start the live stream through the same play path as 0.1.0 (`003-playback.md`) — pause still **stops**. If the stream cannot start (no network / player error), play a **committed local** fallback sound in `res/raw` — not a network asset at ring time. Do not GET Icecast in tests.

Snooze: configurable duration; optional disable. Notification while ringing uses large labeled actions (not icon-only).

## Sleep timer

One-shot timer to stop playback through the same play/stop path (`003-playback.md`). Settings → Alerts: toggle (default **off**) and duration **15 / 30 / 45 / 60 / 90** minutes (default **30**), same cycling row as snooze. Arming stores a deadline in DataStore. The last **15 seconds** fade player gain to 0; do **not** persist the faded gain. Then stop (pause still **stops**). Manual stop (pause/stop, unplug, audio-focus loss) cancels the timer. Changing duration while armed restarts the deadline. Not on Auto. Do not GET Icecast in tests.

## DJ-online notifier

Opt-in, default **off**. Use **WorkManager** periodic + snapshot `dj` / `isafkstream` changes. Do **not** use a `specialUse` foreground service to poll.

No push channel exists from the station. Document battery cost in Settings.
