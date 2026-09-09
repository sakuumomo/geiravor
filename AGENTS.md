# Agent notes — Geiravor

Spec-first Android client for [r-a-d.io](https://r-a-d.io).

## Source of truth

1. `docs/spec/` — product (listener). Live `https://r-a-d.io` and `GET https://r-a-d.io/api` beat examples when the world has moved.
2. `docs/architecture.md`, `docs/build.md`, `docs/testing.md`, `docs/compat.md` — repo.
3. This file — how to work.

Do not implement a feature that is not in a spec. Do not port or copy another Android radio app. Do not copy implementation out of `legacy/0.3` (assets and LICENSE are allowed). Do not consume `/v1/sse`. Pause on a live stream **stops**. Empty nick = fave no-op. Do not GET the Icecast URL in tests. Do not live-`.fave` unless the human asked.

Christmas snow and Halloween hair/moon/pumpkins are **in the committed wallpapers** (`drawable-nodpi/wallpaper_*.jpg`). Live `/assets/{theme}/js/theme.js` is empty and those image dirs only have the wallpaper. Do **not** add particle overlays. Type stays on panes (frost/sheet), not on the wallpaper.

## Planes

Default: **one agent**. Fan-out only with a frozen contract. No Kotlin in parallel with an unfrozen UniFFI surface.

- **docs** — `docs/`, CHANGELOG, README, this file
- **domain** — `core/`
- **shell** — `app/` (Kotlin floor)
- **tooling** — flake, `.github/`, `scripts/`, `.githooks`

## Commands

Work inside `nix develop` (or `nix develop .#rust` for host Cargo).

```
nix develop .#rust -c cargo fmt --all -- --check
nix develop .#rust -c cargo clippy --all-targets -- -D warnings
nix develop .#rust -c cargo test --manifest-path core/Cargo.toml
RUSTDOCFLAGS='-D warnings' nix develop .#rust -c cargo doc --no-deps
./scripts/check-pins
nix develop -c ./gradlew :app:testDebugUnitTest :app:assembleDebug
./scripts/dhu.sh          # discovers serial; --stop; never kill :5277
```

`git config core.hooksPath .githooks` once per clone.

## Verify

Cite the spec. Run the tests; do not claim they passed without the log. Domain is test-first. Waydroid: discover the emulator serial from `adb devices`; never install to an `unauthorized` physical phone. DHU: do not kill `:5277`.

## Git

GPG-signed Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `ci:`, `build:`, `refactor:`, `chore:`). One logical change. No AI trailers.

Never push unless the human says `push`. Never force-push `main`.

Do not commit `local.properties`, `jniLibs/`, `target/`, or keystores.

Semver triad (`versionName` = Cargo = User-Agent `Geiravor/X.Y.Z`) is changed only by `scripts/bump-version`.

## Docs vs rustdoc

AI reads markdown in `docs/` and comments in source. `///` is crate API (`cargo doc`); it links to the spec and does not paste it. Do not treat `target/doc/` as normative. Do not write a hand-written API reference.
