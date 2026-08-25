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

Fake UniFFI core. Cover pause→stop (drop live buffer, no reconnect while stopped), idle notification kept after stop, notification permission path (compat; play does not require it), Auto browse hiding Queue when not AFK.

## CI

`nix develop -c cargo test` and `nix develop -c ./gradlew :app:assembleDebug`. No emulator. Do not GET the Icecast URL.

## Manual (0.1.0)

Phone: play/stop, volume, unplug, notification play/pause (stays after pause; play even if permission denied), rotation, airplane, audio-focus interruption, AFK vs live DJ next `???` / queue hide.

DHU (optional): app appears, play/stop, no skip buttons, prev/next text, browse does not change audio. Not a merge gate for phone work.

Do not claim real-car Auto until someone runs it.
