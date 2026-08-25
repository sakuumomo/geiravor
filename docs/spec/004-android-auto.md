# 004 — Android Auto

Projected Android Auto only (phone → dash). Not Android Automotive OS.

## Mechanism

Media app path: Media3 `MediaLibraryService` + `MediaSession`. No Car App Library.

Manifest:

- Service `android:exported="true"`
- Intent filters: `androidx.media3.session.MediaLibraryService` and `android.media.browse.MediaBrowserService`
- `androidx.media3.session.MediaButtonReceiver` for `MEDIA_BUTTON` (Auto/Bluetooth discovery)
- `automotive_app_desc.xml` with `<uses name="media"/>`
- `com.google.android.gms.car.application` meta-data

Sideload: on the **phone that plugs into the car**, enable Android Auto **Developer settings → Unknown sources**, then reboot the phone. DHU is the same. A Play listing is not required for 0.1.0.

## Commands

Advertise play and pause. Implement pause as **stop** (`003-playback.md`).

Do **not** advertise seek, skip next, or skip previous.

## Now playing card

- Title / artist from split `np`
- Album artist = `dj.djname`
- Artwork = DJ image (mystery-DJ fallback)
- Subtitle, truncated: `Prev: <lp[0]> · Next: <queue[0]>`
- Live DJ (`isafkstream == false`): `Next: ???` (same as the phone Now Playing screen). Do not use `queue[0]`.
- Omit `Prev:` if last-played is empty. Omit `Next:` only when AFK and the queue is empty.
- Duration/position from the AFK API window (`002-api.md`). Live DJ: unknown duration (`TIME_UNSET`).

Tapping previous/next must not exist as a command. The subtitle is reference only.

## Default view

The session now-playing card is the default (same as the phone Now Playing tab): play/pause, `np`, DJ, **Prev/Next text**. Do **not** put Now Playing in the browse tree. The Auto app icon (top left) returns to this default. Showing the default must **not** start playback; Play is explicit, or Settings auto-start.

## Browse tree

Same sections as the phone Songs tab:

1. Last Played (browsable folder)
2. Queue — **only if `isafkstream`**. Same hide rule as Songs.

Last Played / Queue **rows are reference only**: not playable, not browsable. Tapping a song is a no-op (no error, no play). Only the live stream ever plays.

No request, news, thread, search, or settings in Auto.

## DHU

Install Desktop Head Unit via Android Studio extras when testing Auto. Not in the Nix default shell. Phone features must not wait on DHU (`011-testing.md`).
