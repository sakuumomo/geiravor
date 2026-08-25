# 003 — Playback

## Stream

Play `https://stream.r-a-d.io/main.mp3` with Media3 ExoPlayer `ProgressiveMediaSource`. Content-Type `audio/mpeg`. `icy-name: R/a/dio`. No `Content-Length`. `accept-ranges: none`.

Follow HTTPS redirects. Do not enable cleartext unless a live redirect is actually HTTP (none observed).

Do not play `https://r-a-d.io/main`.

Never download the `.mp3` in unit tests or CI.

## Live semantics

There is one playable `MediaItem`: the stream URL. Last played / queue items are display-only.

- **Play** connects (or reconnects) to live with a new HTTP GET. Do not resume a paused buffer.
- **Pause** (Auto, notification, headset UI) **stops**: `stop` + drop the live buffer so Icecast is not left downloading or cached (`playWhenReady = false` is wrong). Then set the live `MediaItem` **without** `prepare` so the notification/Auto play target remains. Explicit Play `prepare`s a new GET (`onPlaybackResumption` if the item is missing).
- **Stop** is the same teardown.
- **Seek**, skip next, skip previous are not advertised and are rejected.
- While the user wants play, player error or stream end **auto-reconnects**. After pause/stop, do **not** reconnect.

AudioAttributes: `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`. ExoPlayer wake mode for network while playing.

## Progress

Song window comes from `/api` (`002-api.md`), converted to milliseconds for Media3 metadata, **only while AFK**. Live DJ: duration `TIME_UNSET`, no track clock. ExoPlayer position is **not** the song progress. Seek on the session is rejected.

## Focus and noisy

- Audio-focus loss → stop
- `ACTION_AUDIO_BECOMING_NOISY` (unplug) → stop
- Optional auto-start on plug (wired headset) and in vehicle (Android Auto / car mode): Settings (`008-settings.md`); both default off. Independent toggles.

## Foreground service

`MediaLibraryService` is bindable while **idle** (Auto discovery). Promote to FGS `mediaPlayback` only while `STATE_PLAYING`. Drop FGS on stop.

Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Start type via compat (`009-compat.md`).

## Notification

Media3 media notification. On API 33+ we may ask `POST_NOTIFICATIONS` on Play so the shade can show it. Playback does **not** wait on grant; deny or ignore still plays and pauses. Channel for playback.

After pause/stop: keep that notification as the play target (unprepared live item, no Icecast GET). Play on it reconnects. Shade dismiss follows the platform session; do not custom-handle swipe.

`setSessionActivity` → the single Activity.

## ICY

ICY metadata may fire on song change. Treat as a refetch trigger only (`002-api.md`). Do not overwrite `np`.

## Volume

Player gain 0.0–1.0, UI 0–100, default **80**. Independent of system stream volume. Persist in DataStore. Control lives on now-playing (phone slider) and Android Auto (same gain, ±5). Not only Settings.

## 0.2.0

Alarm and sleep timer use the same play/stop path (`010-alarm-sleep-dj.md`). Alarm fallback sound is local, not the stream.
