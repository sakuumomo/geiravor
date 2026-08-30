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

App gain is the same as the phone (0–100, default 80, DataStore). Independent of the car's system volume. Compact **unfocused** Auto has two custom slots beside play: **Mute** (back) and **Fave** (forward). Focused now-playing also has **Vol −** (back secondary) and **Vol +** (forward secondary, right of Fave), ±5. Mute toggles app gain to 0 and back to the last non-zero gain (default 80 if none).

## Now playing card

- Title = split `np` title. Subtitle = artist. Description = `dj.djname`. No `| DJ` on Auto (the phone shade owns that compact line).
- Unfocused now-playing is title and artist. Focused now-playing is title, artist, then DJ on its own line.
- No artist (`""` or whitespace): subtitle is DJ only, no description line. Do not leave a blank artist line.
- No next/prev on Auto chrome. Artwork = DJ image (mystery-DJ fallback).
- Those fields live on the stream `MediaItem`. Auto only uses subtitle/description when `displayTitle` is set.
- Duration/position from the AFK API window (`002-api.md`) on both **unfocused** and **focused** now-playing so the platform can draw a progress bar. Not seekable. Live DJ: unknown duration (`TIME_UNSET`), no bar.

Tapping previous/next must not exist as a command.

## Default view

The session now-playing card is the default (same as the phone Now Playing tab): play/pause, `np`, DJ. Do **not** put Now Playing in the browse tree. The Auto app icon (top left) returns to this default. Showing the default must **not** start playback; Play is explicit, or Settings auto-start.

Pause still **stops** Icecast. Keep the live `MediaItem`. After the user pauses, report that item as paused `STATE_READY` (not idle) so Auto keeps now-playing and the bottom-right control. Do **not** do this on Auto connect. Play `prepare`s a new GET. Settings taps must not be required to restore the card.

## Browse tree

Library root is browsable, **not** playable, and has no stream URI. Showing the default must not start playback.

Root tabs (both `FLAG_BROWSABLE`, never playable):

1. **Songs** — Last Played folder; Queue folder only if `isafkstream` (same hide as the phone Songs tab).
2. **Settings** — Auto-start in vehicle, Auto-start on plug (subtitle On/Off), About.

Last Played / Queue **rows are reference only**: not playable, not browsable. Auto still shows **No items** if a row is tapped (the platform has no inert track). Do not play and do not open now-playing. Only the live stream ever plays. A tap must not return the live `MediaItem`.

Settings auto-start rows are playable **function** items (not the live stream). Tap flips the DataStore flag. While the stream is actually playing, do not replace or re-prepare the live item. After pause (real idle), Play must be allowed to `setMediaItem`/`prepare` again. Do not swallow that Play with a timer. Auto may still open now-playing; suppress only the follow-up Play from that settings tap while paused. Back shows the updated On/Off subtitle. About is display-only. `getChildren` is read-only and must not return a node as a child of itself. `notifyChildrenChanged` only when that parent’s children actually changed. A `/api` poll with the same Last Played / Queue must not reload those lists. Settings is not notified from a status poll.

When **Auto-start in vehicle** is on, projected Auto session connect starts the live stream. Connect with the setting off must not play.

Do not advertise `COMMAND_GET_TIMELINE` (hides Auto’s empty Queue button). Do not add-to-playlist.

A **Fave** custom action on the now-playing card (`LivePlaybackPolicy.FAVE`) uses the same IRC add-fave path as the phone (`006-requests-faves.md`). Empty nick → no-op (keep advertising the command). It must not start, stop, or replace the live item, and must not rewrite now-playing metadata to display the result. Heart is **filled** when the current song is already a favorite (cached `/faves` rows or a successful Fave this session); outline otherwise. Same icon on the media notification custom action. On Auto, the compact **unfocused** card is Mute (back) and Fave (forward — skip-next is not advertised). Focused adds Vol − (back secondary) and Vol + (forward secondary). Use media button preferences (not custom-layout list order).

No request, news, schedule, staff, thread, or search in Auto. No Favorites browse folder. Dark/light on Auto follows the app theme pick (`008-settings.md`). Holiday token packs are phone-only.

## DHU

Desktop Head Unit is the car screen. Waydroid (or a physical phone) is still the phone; DHU does not replace the phone.

In `nix develop`, `extras-google-auto` provides `desktop-head-unit` (FHS-wrapped). Pair with Waydroid:

1. Waydroid running, Geiravor and **Android Auto** installed in it (Play Store).
2. Android Auto → ⋮ → **Start head unit server** (once per session).
3. `./scripts/dhu.sh` (adb connect + `adb forward tcp:5277 tcp:5277` + DHU).

Unknown sources still apply inside Waydroid. DHU is the Auto test rig. It is **not** a merge gate (`011-testing.md`). Phone features must not wait on it.

Projected Auto has been run in a real car for 0.1.0. Sideload + Unknown sources still apply on a phone that is not from Play.
