# Geiravor

Android app for [r/a/dio](https://r-a-d.io). User-facing name: **r/a/dio**. Package: `io.r_a_d.geiravor`.

License: MIT (`LICENSE.md`). Copyright © 2026 Sakurai Momoka. Version: **0.2.0**.

## What 0.1.0 is

Play and stop the live stream, see now playing / last played / queue, and control playback from the notification and Android Auto. UI follows the live **default-dark** site.

## What 0.2.0 is

Search and request on AFK, nick favorites (list + IRC add-fave), news, alarm/snooze, sleep timer, and an opt-in DJ-online notifier. Phone tabs Now Playing | Songs | News | Settings.

## What 0.3.0 is

Schedule and staff on a **Board** tab (News | Schedule | Staff), extra themes (Default light plus Christmas / Halloween / New Years with wallpapers), and disk writes only when the payload changed. Phone tabs Now Playing | Songs | Board | Settings. Version bump happens when this drop ships; current tagged release is still 0.2.0.

## Build

Nix is the supported toolchain:

```
nix develop          # or: direnv allow
cargo test --manifest-path core/Cargo.toml
./gradlew :app:assembleDebug
```

Needs a flake-capable Nix. `aarch64-linux` is not a 0.1.0 flake target.

Android Auto: projected (phone → dash). A real car has been used for 0.1.0. Sideload still needs Auto **Unknown sources**. Desktop Head Unit is in `nix develop` (`./scripts/dhu.sh`) for testing; it is not a merge gate.

## Specs

Normative docs: [`docs/spec/`](docs/spec/README.md). Working in this repo: [`AGENTS.md`](AGENTS.md).
