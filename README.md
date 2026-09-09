# Geiravor

Android app for [r/a/dio](https://r-a-d.io). User-facing name: **r/a/dio**. Package: `io.r_a_d.geiravor`.

License: MIT (`LICENSE.md`). Copyright © 2026 Sakurai Momoka.

**1.0.0** is a from-scratch rewrite of 0.3.0. The last 0.3.0 source is `legacy/0.3` (tag `v0.3.0`).

[![CI](https://github.com/sakuumomo/geiravor/actions/workflows/ci.yml/badge.svg)](https://github.com/sakuumomo/geiravor/actions/workflows/ci.yml)

## Build

Nix is the supported toolchain:

```
nix develop          # full shell (JDK, Android SDK/NDK, DHU on Linux)
nix develop .#rust   # host Rust only (fmt, clippy, tests, rustdoc)
cargo test --manifest-path core/Cargo.toml
./gradlew :app:testDebugUnitTest :app:assembleDebug
./scripts/dhu.sh          # DHU; waits for Head Unit Server; --stop to end
```

Needs a flake-capable Nix. `aarch64-linux` is not a flake target.

After clone:

```
git config core.hooksPath .githooks
```

## Docs

| File | Job |
|---|---|
| [`docs/spec/`](docs/spec/) | Product contract (what a listener gets) |
| [`docs/`](docs/README.md) | Repo contract vs product split |
| [`AGENTS.md`](AGENTS.md) | How to work in this tree |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Pointer to AGENTS and docs |
| [`SECURITY.md`](SECURITY.md) | Vulnerability reports |
| [`CHANGELOG.md`](CHANGELOG.md) | Version history of **this** tree |

Generated crate docs: `nix develop .#rust -c cargo doc --no-deps --open` (not a second spec).
