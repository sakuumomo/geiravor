# Geiravor

Android app for [r/a/dio](https://r-a-d.io). User-facing name: **r/a/dio**. Package: `io.r_a_d.geiravor`.

License: MIT (`LICENSE.md`). Copyright © 2026 Sakurai Momoka. Version: **0.1.0**.

## What 0.1.0 is

Play and stop the live stream, see now playing / last played / queue, and control playback from the notification and Android Auto. UI follows the live **default-dark** site.

## Build

Nix is the supported toolchain:

```
nix develop          # or: direnv allow
cargo test --manifest-path core/Cargo.toml
./gradlew :app:assembleDebug
```

Needs a flake-capable Nix. `aarch64-linux` is not a 0.1.0 flake target.

Android Auto on a real car may need Play or Auto “Unknown sources”. Desktop Head Unit is optional and not in the default dev shell.

## Specs

Normative docs: [`docs/spec/`](docs/spec/README.md). Working in this repo: [`AGENTS.md`](AGENTS.md).
