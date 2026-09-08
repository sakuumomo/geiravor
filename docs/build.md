# Build

Supported flow: **Nix flake**. Cargo and Gradle run inside `nix develop`.

## Semver

`versionName` = Cargo `version` = User-Agent `Geiravor/X.Y.Z`. Those three strings are always identical. `versionCode` is a separate monotonic integer (do not encode X.Y.Z into it).

Change them **only** via `scripts/bump-version`. Agents never hand-edit the triad. `scripts/check-pins` (hooks + CI) fails if they drift, if Gradle `ndkVersion` ≠ flake `ndkVersion`, or if `android-nixpkgs` `follows` nixpkgs.

This rewrite tree stays **0.3.0** / `versionCode` **3** until that script at **1.0.0** / **4**.

After 1.0.0:

| Bump | When |
|---|---|
| MAJOR | Listener-incompatible: drop a capability, schema without migration, minSdk, package, pause/fave/identity semantics |
| MINOR | New listener-facing capability |
| PATCH | Fixes, polish, less network, internal, docs, CI |

Do **not** bump the app for fmt, tests-only, agent docs, or `flake.lock` refresh. `nix flake update` is a `build:` commit.

[`CHANGELOG.md`](../CHANGELOG.md) is this tree’s history (`[Unreleased]` then dated `[X.Y.Z]`). GitHub Release body = that section. Do not invent 0.1.0–0.3.0 sections here; that history is `legacy/0.3`.

## Android

- minSdk 26, compileSdk 36, targetSdk 36 (Play’s 2026 bar for phones + Auto)
- JDK 21
- Kotlin 2.x, Compose (when `app/` exists)
- NDK **r28+** (16 KB pages by default), **one** string in flake and Gradle (`28.2.13676358` / package `ndk-28-2-13676358`)
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- cargo-ndk `--platform 26`
- R8 on for release with UniFFI/JNA keep rules

No `rust-toolchain.toml`. Fenix in the flake owns rustc.

## Flake

Inputs (do **not** set `android-nixpkgs.inputs.nixpkgs.follows`):

- `nixpkgs`: `nixos-unstable`
- `android-nixpkgs`: `github:tadfisher/android-nixpkgs/stable`
- `fenix`: `github:nix-community/fenix` with `inputs.nixpkgs.follows = "nixpkgs"`

`allowUnfree = true`, `android_sdk.accept_license = true`.

Shells:

- `nix develop .#rust` — host cargo, rustfmt, clippy, rust-src, rust-analyzer, nixfmt. **CI uses this.**
- `nix develop` (`default`) — that plus JDK, SDK/NDK, cargo-ndk, DHU FHS wrap on Linux

SDK: `cmdline-tools-16-0` (not `latest`: 23’s `android` CLI wrapper fails in Nix), `platform-tools`, `platforms-android-36`, `build-tools-36-0-0`, `ndk-28-2-13676358`, `cmake-3-22-1`, `extras-google-auto` (DHU). No emulator in the shell.

DHU 2.1 is the last Google extra; its TLS check fails on a 2026 clock. The wrap fakes the process clock (`DHU_FAKETIME`, default `@2024-06-01 12:00:00`, `FAKETIME_DONT_FAKE_MONOTONIC=1`). `buildFHSEnv` is Linux-only so Darwin `nix develop` still evals.

Fenix stable + android `rust-std` for `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android` in **default** only.

Systems: `x86_64-linux`, `x86_64-darwin`, `aarch64-darwin`. Not `aarch64-linux`.

Env on default: `JAVA_HOME`, `ANDROID_HOME` = `ANDROID_SDK_ROOT`, `ANDROID_NDK_HOME` = `ANDROID_NDK_ROOT` = `…/ndk/<exact-version>`, `ANDROID_USER_HOME` under `$XDG_CACHE_HOME/geiravor/android`, `LIBCLANG_PATH`, `GRADLE_OPTS` aapt2 override (required on NixOS; do not drop it). `stdenv.isLinux` in android-nixpkgs is patched to `stdenv.hostPlatform.isLinux`.

`shellHook` writes gitignored `local.properties` only if the Gradle wrapper exists.

`.envrc`: `use flake`. Prefer nix-direnv.

DevShell only — not `buildGradlePackage`.

## Hooks

`.githooks` + `git config core.hooksPath .githooks` (once per clone). Fast: rustfmt, nixfmt, Conventional Commit subject, no AI trailers, triad/NDK pins, forbidden files. Not a full test suite. Not husky.

## Git

Commit: wrapper (when present), `Cargo.lock`, `flake.lock`, specs, sources, vendored images.

Do not commit: `local.properties`, `jniLibs/`, `target/`, `.direnv/`, keystores.

Repo files must not name out-of-tree scratch directories (including this machine’s Waydroid IP).

## CI

Pinned Nix installer (not `@main`). `nix develop .#rust` for fmt, clippy `-D warnings`, `cargo test`, rustdoc `-D warnings`, `scripts/check-pins`. Gradle unit tests and `assembleDebug` **when `app/` exists**. Free unused runner Android/.NET/etc. so the SDK fits. No emulator, no DHU, no Icecast GET, no live `/api` or Rizon.

Before the public 1.0.0 force-push, run that command set **locally**.
