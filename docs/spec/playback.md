# Playback

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

Song window comes from `/api` ([api.md](api.md)), converted to milliseconds for Media3 metadata **and** `MediaMetadata.durationMs`, **only while AFK**. Live DJ: duration `TIME_UNSET`, no track clock. ExoPlayer position is **not** the song progress. Seek on the session is rejected.

Report `isCurrentMediaItemLive` only when that duration is unknown. Icecast is still a live GET; the AFK song window is what Auto and the shade use for a **non-seekable** progress bar. Do not report Icecast buffered bytes as the song buffer.

## Focus and noisy

- Audio-focus loss → stop
- `ACTION_AUDIO_BECOMING_NOISY` (unplug) → stop
- Optional auto-start on plug (wired headset) and in vehicle (Android Auto / car mode): Settings ([settings.md](settings.md)); both default off. Independent toggles.

## Foreground service

`MediaLibraryService` is bindable while **idle** (Auto discovery). After Play or alarm, call `startForeground` **immediately** with the playback notification (play/stop, mute, Fave, Vol − / Vol +; no next/prev) so Android’s FGS timeout is met while Icecast buffers — do not wait for `STATE_PLAYING` or artwork. Drop FGS when `playWhenReady` becomes false (pause/stop, Auto pause, unplug, audio-focus loss) — not on a buffer blip. Domain `playing` is that same want-play flag; stream-down still clears only when Icecast is actually playing.

Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Start type via compat.

## Notification

The playback shade is a **media-style** notification the app posts (not Media3’s idle-player provider). Channel `geiravor_playback` (`IMPORTANCE_LOW`), not the alerts channel. Notification id **1001** (Media3’s default) so there is only one playback notification. Title is the current track (up to two lines). Second line is `artist | dj.djname` (the DJ’s name, never the word “DJ”). One line; newlines are not rendered. Ellipsize the artist so `| {dj.djname}` stays visible; do not ellipsize away the `|`. Fit that line to remaining shade width (notification Line2 typeface and letter spacing; window width minus the system large-icon slot and the compact play control). Do not use a character cap. No artist (`""` or whitespace): DJ name only, no pipe. No next/prev on the shade. Compact actions are play/stop, **Mute**, and **Fave**. Expanded also has **Vol −** and **Vol +** (±5, same gain as the phone slider). Mute is a two-wave speaker when sound is on and a slashed speaker when muted (same glyphs on Now Playing, the shade, and Auto). Vol − is a speaker with a minus; Vol + is a speaker with a plus. Mute toggles app gain to 0 and back to the last non-zero gain. Fave uses a filled heart when the current song is already a favorite, outline otherwise (same rule as Auto and the phone). Artwork is the Coil DJ-image still (GIF first frame) as the large icon. Mystery-DJ only on error/miss. On API 33+ we may ask `POST_NOTIFICATIONS` on Play so the shade can show it. Playback does **not** wait on grant; deny or ignore still plays and pauses.

`/api` metadata (`np`, DJ name, `djimage`, artwork URI, AFK duration / live `TIME_UNSET`) is applied **in place** on the current live item (same URI / mediaId). Do not rebuild the Icecast `MediaSource` or start a new GET on a snapshot. Pause still **stops**.

After pause/stop: keep that notification as the play target (unprepared live item, no Icecast GET) until the user dismisses it. Post it **once** on stop; `/api` polls must not rewrite or cancel it. If the system or the user clears it, leave it gone until the next Play or alarm. It is **not** ongoing while stopped — swipe-away dismisses it and it must **not** come back until the next Play or alarm. Media3 must not post id 1001 (including after dismiss while the session reports paused `STATE_READY`). The session reports paused `STATE_READY` for that item (including on Auto connect before the first Play) so compact/focused now-playing stay available. Play on it reconnects.

`setSessionActivity` → the single Activity.

## ICY

ICY metadata may fire on song change. Treat as a refetch trigger only ([api.md](api.md)). Do not overwrite `np`. Parse `Icy-Tags` / `StreamTags` from the raw ICY block for live-DJ Now Playing tags when `/api` `tags` is empty.

## Volume

Player gain 0.0–1.0, UI 0–100, default **80**. Independent of system stream volume. Persist. Control lives on now-playing (phone slider **and mute**), the shade (mute and ±5), and Android Auto (same gain, ±5). Mute toggles to 0 and back to the last non-zero gain (default 80 if none). Not only Settings.

## Alarm and sleep

Alarm and sleep timer use the same play/stop path ([alarm-sleep-dj.md](alarm-sleep-dj.md)). Alarm fallback sound is local, not the stream.
