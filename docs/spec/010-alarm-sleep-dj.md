# 010 — Alarm, sleep, DJ notifier (0.2.0)

Not built in 0.1.0.

## Alarm

Exact alarm via `AlarmManager` + compat (`009-compat.md`). On fire: start the live stream through the same play path as 0.1.0 (`003-playback.md`). If the stream cannot start (no network / player error), play a **committed local** fallback sound — not a network asset at ring time.

Snooze: configurable duration; optional disable. Notification while ringing uses large labeled actions (not icon-only).

## Sleep timer

Timer to stop playback. Fade out near the end. Cancel on manual stop.

## DJ-online notifier

Opt-in, default **off**. Use **WorkManager** periodic + snapshot `dj` / `isafkstream` changes. Do **not** use a `specialUse` foreground service to poll.

No push channel exists from the station. Document battery cost in Settings.
