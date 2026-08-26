# 011 — Testing

## Rust

Committed fixtures under `core/tests/fixtures/` captured from `https://r-a-d.io/api` (never from a live `.mp3`).

Must cover:

- JSON parse of `main` (ignore HTML `time`)
- `np` split (including no-hyphen and extra `" - "` in the title)
- Progress offset with unix **seconds**
- Queue hidden when `isafkstream == false`
- Live DJ ignores catalog `start_time` / `end_time`
- `end_time == 0` unknown duration (AFK)
- Poll interval floor 2s / backoff
- ICY change triggers refetch and does not overwrite `np`

`cargo test` on the host; no NDK.

## Kotlin

Fake UniFFI core. Cover pause→stop (drop live buffer, no reconnect while stopped), idle notification kept after stop, playlist kept on stop, Play after stop is not swallowed, notification permission path (compat; play does not require it), Auto root Songs+Settings, Queue folder hidden when not AFK, settings toggle rows, auto-start on plug and in vehicle default off (independent), Songs Queue section hidden when not AFK, volume ±5 for Auto, `COMMAND_GET_TIMELINE` removed (no Queue chrome), Fave stub, live `MediaItem` carries split `np` / DJ artwork. Two-pane at ≥840dp.

## CI

`nix develop -c cargo test --manifest-path core/Cargo.toml`, `nix develop -c ./gradlew :app:testDebugUnitTest`, and `nix develop -c ./gradlew :app:assembleDebug`. No emulator. Do not GET the Icecast URL. DHU FHS wrap is Linux-only.

## Manual (0.1.0)

Phone: play/stop, volume, unplug, notification play/pause (stays after pause; play even if permission denied), rotation, airplane, audio-focus interruption, AFK vs live DJ next `???` / queue hide, Settings auto-start on plug / in vehicle (both default off) and version. Tablet (width ≥ 840dp and smallest width ≥ 600dp): Now Playing stays left; Songs/Settings switch the right pane. 16:9 phone landscape stays single-pane.

DHU: keep `nix develop -c ./scripts/dhu.sh` (Waydroid + Android Auto Head Unit Server) as the Auto test rig. App appears, play/stop, no skip buttons, no Queue chrome, title plus artist unfocused / title-artist-DJ focused, Songs and Settings tabs, browse does not change audio. **Not a merge gate.** Phone and real-car work do not wait on DHU. CI does not run DHU.

Projected Android Auto has been run in a real car for 0.1.0. That does not cover every OEM skin.
