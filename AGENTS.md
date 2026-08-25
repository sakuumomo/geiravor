# Agent notes — Geiravor

Spec-first Android client for [r-a-d.io](https://r-a-d.io).

## Source of truth

1. `docs/spec/` (normative)
2. Live `https://r-a-d.io` and `GET https://r-a-d.io/api` when the world has moved on
3. This file for how to work in the tree

Do not implement a feature that is not in a spec. Do not port or copy another Android radio app. Do not consume `/v1/sse` (HTML).

## Layout

- `core/` — Rust: models, `/api`, poll, reducer (UniFFI)
- `app/` — Kotlin: Compose, Media3, Auto, notifications, `compat/`
- Pause on a live stream **stops** (`docs/spec/003-playback.md`)

## Compat

API-gated Android behavior lives in `app/.../compat/` with:

```
/** Compat: POST_NOTIFICATIONS. Remove when minSdk >= 33. */
```

No scattered `SDK_INT` in features.

## Versions

Semver `X.Y.Z` is identical in `versionName`, Cargo `version`, and User-Agent `Geiravor/X.Y.Z`. `versionCode` is a separate monotonic integer. Current: **0.1.0** / `versionCode` 1.

## Commands

```
nix develop
cargo test --manifest-path core/Cargo.toml
./gradlew :app:assembleDebug
```

Host `cargo test` does not need the NDK. Do not GET the Icecast URL in tests.

## Git

Do not commit `local.properties`, `jniLibs/`, `target/`, or keystores.
