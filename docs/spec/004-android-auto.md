# 004 — Android Auto

Projected Android Auto only (phone → dash). Not Android Automotive OS.

## Mechanism

Media app path: Media3 `MediaLibraryService` + `MediaSession`. No Car App Library.

Manifest:

- Service `android:exported="true"`
- Intent filters: `androidx.media3.session.MediaLibraryService` and `android.media.browse.MediaBrowserService`
- `automotive_app_desc.xml` with `<uses name="media"/>`
- `com.google.android.gms.car.application` meta-data

Sideload works with DHU. A real car often needs Play or Auto “Unknown sources”. 0.1.0 does not assume a Play listing.

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

## Browse tree

Root:

1. Now Playing (the live stream; playable)
2. Last Played (children not playable; click does not change audio)
3. Queue — **only if `isafkstream`**. Same hide rule as the Songs tab.

Queue/LP `MediaItem`s are display/browse only.

No request, news, thread, search, or settings in Auto.

## DHU

Install Desktop Head Unit via Android Studio extras when testing Auto. Not in the Nix default shell. Phone features must not wait on DHU (`011-testing.md`).
