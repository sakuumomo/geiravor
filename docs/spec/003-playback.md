# 003 — Playback

## Stream

Play `https://stream.r-a-d.io/main.mp3` with Media3 ExoPlayer `ProgressiveMediaSource`. Content-Type `audio/mpeg`. `icy-name: R/a/dio`. No `Content-Length`. `accept-ranges: none`.

Follow HTTPS redirects. Do not enable cleartext unless a live redirect is actually HTTP (none observed).

Do not play `https://r-a-d.io/main`.

Never download the `.mp3` in unit tests or CI.

## Live semantics

There is one playable `MediaItem`: the stream URL. Last played / queue items are display-only.

- **Play** connects (or reconnects) to live.
- **Pause** (Auto, notification, headset UI) **stops**: release the player, do not leave a live buffer paused (`playWhenReady = false` is wrong).
- **Stop** is the same teardown.
- **Seek**, skip next, skip previous are not advertised and are rejected.

AudioAttributes: `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`. ExoPlayer wake mode for network while playing.

## Progress

Song window comes from `/api` (`002-api.md`), converted to milliseconds for Media3 metadata. ExoPlayer position is **not** the song progress. Seek on the session is rejected.

## Focus and noisy

- Audio-focus loss → stop
- `ACTION_AUDIO_BECOMING_NOISY` (unplug) → stop
- Optional auto-start on plug: Settings (`008-settings.md`); default off

## Foreground service

`MediaLibraryService` is bindable while **idle** (Auto discovery). Promote to FGS `mediaPlayback` only while `STATE_PLAYING`. Drop FGS on stop.

Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Start type via compat (`009-compat.md`).

## Notification

Media3 media notification. Runtime `POST_NOTIFICATIONS` on API 33+ **before the first notification** (on Play). Channel for playback.

`setSessionActivity` → the single Activity.

## ICY

ICY metadata may fire on song change. Treat as a refetch trigger only (`002-api.md`). Do not overwrite `np`.

## Volume

Player gain 0.0–1.0, UI 0–100, default **80**. Independent of system stream volume. Persist in DataStore. Control lives on now-playing, not only Settings.

## 0.2.0

Alarm and sleep timer use the same play/stop path (`010-alarm-sleep-dj.md`). Alarm fallback sound is local, not the stream.
