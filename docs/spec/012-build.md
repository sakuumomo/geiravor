# 012 — Build

Supported flow: **Nix flake**. Gradle and Cargo run inside `nix develop`.

## Versions (app)

| Field | 0.1.0 | 0.2.0 | 0.3.0 (current) |
|---|---|---|---|
| `versionName` | `0.1.0` | `0.2.0` | `0.3.0` |
| Cargo `version` | `0.1.0` | `0.2.0` | `0.3.0` |
| User-Agent | `Geiravor/0.1.0` | `Geiravor/0.2.0` | `Geiravor/0.3.0` |
| `versionCode` | `1` | `2` | `3` |

Keep the three semver strings identical (`000-product.md`). Current ship is **0.3.0** / `versionCode` 3.

## Android

- minSdk 26, compileSdk 36, targetSdk 36
- JDK 21
- Kotlin 2.x, Compose
- NDK **r28+** (16 KB pages by default), same string in flake package name and Gradle `ndkVersion`
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- cargo-ndk `--platform 26`
- R8 on for release with UniFFI/JNA keep rules

## Flake

Inputs (do **not** set `android-nixpkgs.inputs.nixpkgs.follows`):

- `nixpkgs`: `nixos-unstable`
- `android-nixpkgs`: `github:tadfisher/android-nixpkgs/stable`
- `fenix`: `github:nix-community/fenix` with `inputs.nixpkgs.follows = "nixpkgs"`

`allowUnfree = true`, `android_sdk.accept_license = true`.

SDK: `cmdline-tools-16-0` (not `latest`: 23’s `android` CLI wrapper fails in Nix), `platform-tools`, `platforms-android-36`, `build-tools-36-0-0`, `ndk-28-2-13676358`, `cmake-3-22-1`, `extras-google-auto` (DHU). No emulator in `devShells.default`. DHU is wrapped in `buildFHSEnv` as `desktop-head-unit`; `./scripts/dhu.sh` pairs it with Waydroid.

Fenix: stable cargo, rustc, rustfmt, clippy, rust-src, rust-analyzer + android `rust-std` for `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`.

Systems: `x86_64-linux`, `x86_64-darwin`, `aarch64-darwin`. **Not** `aarch64-linux` in 0.1.0.

Env (mkShell, not only hook):

- `JAVA_HOME`, `ANDROID_HOME` = `ANDROID_SDK_ROOT` = `${sdk}/share/android-sdk`
- `ANDROID_NDK_HOME` = `ANDROID_NDK_ROOT` = `${ANDROID_HOME}/ndk/<exact-version>`
- `ANDROID_USER_HOME` writable under `$XDG_CACHE_HOME/geiravor/android` (set in `shellHook`; needs `$HOME`)
- `LIBCLANG_PATH`
- `GRADLE_OPTS=-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_SDK_ROOT/build-tools/36.0.0/aapt2` (required on NixOS; AGP prints an “experimental” warning. Do not drop it.)
- `stdenv.isLinux` in android-nixpkgs is patched to `stdenv.hostPlatform.isLinux` in `flake.nix` so `nix develop` stays quiet.

Optional `shellHook` writes gitignored `local.properties` `sdk.dir=...`.

`.envrc`: `use flake`. Prefer nix-direnv.

DevShell only — not `buildGradlePackage` in 0.1.0.

## Git

Commit: wrapper, `Cargo.lock`, specs, sources, vendored images.

Do not commit: `local.properties`, `jniLibs/`, `target/`, `.direnv/`, keystores.

Repo files must not name out-of-tree scratch directories.

## CI

GitHub Actions: drop unused runner Android/.NET/Haskell/CodeQL (Nix supplies the SDK), Nix installer, `nix develop -c cargo test --manifest-path core/Cargo.toml`, delete `target/` (host debug must not sit beside three `cargo-ndk --release` ABIs), `nix develop -c ./gradlew :app:testDebugUnitTest`, `nix develop -c ./gradlew :app:assembleDebug`. Debug signing only in 0.1.0. `buildFHSEnv` for DHU is Linux-only so Darwin `nix develop` still evals.
