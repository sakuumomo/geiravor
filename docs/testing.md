# Testing

Product rules live in [`spec/`](spec/). This file is how we check them. Do not restate the product spec here.

## Domain (`core/`)

Host `cargo test`. No NDK. Domain is **test-first**.

Committed fixtures under `core/tests/fixtures/` from live HTML/JSON (prefer a live check; reuse `legacy/0.3` fixtures only if they still match). Never from a live `.mp3`.

Do **not** open `r-a-d.io`, `irc.rizon.net`, or Icecast from unit tests or CI. Emulate locally: JSON/HTML fixtures for HTTP; scripted IRC plus a localhost TLS listener (001, PING, Hanyuu NOTICE) for add-fave. Never log `AUTHENTICATE` payloads.

Cover the product spec, including at least:

- Snapshot parse (`main`, `tags: null`, extra keys, unix-second clocks)
- `np` split, queue hide when not AFK, live DJ ignores catalog duration
- Poll interval 2s / 15s / backoff
- ICY refetch does not overwrite `np`
- HTML page pipeline (news / schedule / staff) and theme name from `/assets/{name}/`
- Schedule Monday-first, empty day is not Hanyuu, local-time rewrite
- Disk equality: unchanged payload is not written
- IRC: AFK `.fave {id}`, live-DJ accuracy, bouncer no-QUIT, IPv6 leftover timeout, nick rename does not fave, SASL
- Last-paint restore so a tap before the first poll is not empty

## Shell (`app/`)

Fake UniFFI core. Cover pause→stop, notification/Auto session with the Activity dead, permission paths (play does not require `POST_NOTIFICATIONS`), Auto browse tree, cold Auto heart/membership, theme night mode, pane/glass rules that are not parseable in Rust.

## CI

[`build.md`](build.md). `nix develop .#rust` fmt, clippy, test, rustdoc, pins. Gradle: `:app:testDebugUnitTest` and `:app:assembleDebug`.

## Device

Waydroid is the phone. Discover the emulator serial from `adb devices`. Never install to an `unauthorized` physical phone. Do not install rewrite APKs on the daily driver until 1.0.0.

DHU is the Auto rig (`scripts/dhu.sh`): discovers the Waydroid/adb serial, waits for Head Unit Server on `:5277`, faketime wrap, do **not** kill `:5277` / force-stop the Auto app. `--stop` ends desktop-head-unit only. **Not a merge gate.** CI does not run DHU.

Manual: play/stop, volume, unplug, notification, AFK vs live DJ, Board sections, themes, Auto play/stop with no skip. Phone features must not wait on DHU.
