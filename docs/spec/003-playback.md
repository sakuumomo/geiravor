# 003 — Playback

## Stream

Play `https://stream.r-a-d.io/main.mp3` with Media3 ExoPlayer `ProgressiveMediaSource`. Content-Type `audio/mpeg`. `icy-name: R/a/dio`. No `Content-Length`. `accept-ranges: none`.

Follow HTTPS redirects. Do not enable cleartext unless a live redirect is actually HTTP (none observed).

Do not play `https://r-a-d.io/main`.

Never download the `.mp3` in unit tests or CI.

## Live semantics

There is one playable `MediaItem`: the stream URL. Last played / queue items are display-only.

- **Play** connects (or reconnects) to live with a new HTTP GET. Do not resume a paused buffer. After idle/stop, `prepare` a fresh source (same URL, new GET to the live edge).
- **Pause** (Auto, notification, headset UI) **stops**: `stop` + drop the live buffer so Icecast is not left downloading or cached (`playWhenReady = false` is wrong). Keep the same live `MediaItem` **without** `prepare` (do not `clearMediaItems`). Explicit Play `prepare`s a new GET. Do not swallow that Play with `ignoreNextPlay`.
- **Stop** is the same teardown.
- The same teardown applies to every pause of **actual playback** (not mute / gain 0): audio-focus loss (phone call, another app), becoming-noisy (unplug), and Auto ↔ phone handoff that pauses the inner player. Those paths must not leave ExoPlayer paused-but-loading. Mute is volume only.
- **Seek**, skip next, skip previous are not advertised and are rejected.
- While the user wants play, player error or stream end **auto-reconnects** (2s delay). After pause/stop, do **not** reconnect. Player error still marks stream-down even while reconnecting. Clear stream-down when the player is actually playing again.

AudioAttributes: `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`. ExoPlayer wake mode for network while playing.

## Progress

Song window comes from `/api` (`002-api.md`), converted to milliseconds for Media3 metadata **and** `MediaMetadata.durationMs`, **only while AFK**. Live DJ: duration `TIME_UNSET`, no track clock. ExoPlayer position is **not** the song progress. Seek on the session is rejected.

Report `isCurrentMediaItemLive` only when that duration is unknown. Icecast is still a live GET; the AFK song window is what Auto and the shade use for a **non-seekable** progress bar. Do not report Icecast buffered bytes as the song buffer.

## Focus and noisy

- Audio-focus loss → stop
- `ACTION_AUDIO_BECOMING_NOISY` (unplug) → stop
- Optional auto-start on plug (wired headset) and in vehicle (Android Auto / car mode): Settings (`008-settings.md`); both default off. Independent toggles.

## Foreground service

`MediaLibraryService` is bindable while **idle** (Auto discovery). Promote to FGS `mediaPlayback` only while `STATE_PLAYING`. Drop FGS on stop.

Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Start type via compat (`009-compat.md`).

## Notification

Media3 media notification. Title is the current track (up to two lines). Second line is `artist | dj.djname` (the DJ’s name, never the word “DJ”). One line; newlines are not rendered. Ellipsize the artist so `| {dj.djname}` stays visible; do not ellipsize away the `|`. Fit that line to remaining shade width (notification Line2 typeface and letter spacing; window width minus the system large-icon slot and the compact play control). Do not use a character cap. No artist (`""` or whitespace): DJ name only, no pipe. No next/prev on the shade. Fave custom action uses a filled heart when the current song is already a favorite, outline otherwise (same rule as Auto and the phone). Artwork uses the Coil DJ-image cache, flattened to a still (GIF first frame). Mystery-DJ only on error/miss. On API 33+ we may ask `POST_NOTIFICATIONS` on Play so the shade can show it. Playback does **not** wait on grant; deny or ignore still plays and pauses. Channel for playback.

`/api` metadata (`np`, DJ name, `djimage`, artwork URI, AFK duration / live `TIME_UNSET`) is applied **in place** on the current live item (same URI / mediaId). Do not rebuild the Icecast `MediaSource` or start a new GET on a snapshot. Pause still **stops**.

After pause/stop: keep that notification as the play target (unprepared live item, no Icecast GET). The session reports paused `STATE_READY` for that item (including on Auto connect before the first Play) so compact/focused now-playing stay available. Play on it reconnects. Shade dismiss follows the platform session; do not custom-handle swipe.

`setSessionActivity` → the single Activity.

## ICY

ICY metadata may fire on song change. Treat as a refetch trigger only (`002-api.md`). Do not overwrite `np`.

## Volume

Player gain 0.0–1.0, UI 0–100, default **80**. Independent of system stream volume. Persist in DataStore. Control lives on now-playing (phone slider) and Android Auto (same gain, ±5). Not only Settings.

## 0.2.0

Alarm and sleep timer use the same play/stop path (`010-alarm-sleep-dj.md`). Alarm fallback sound is local, not the stream.
