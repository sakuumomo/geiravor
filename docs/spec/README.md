# Geiravor specifications

These files are **normative**. Code implements them. Live [r-a-d.io](https://r-a-d.io) and `GET https://r-a-d.io/api` beat any example in this tree when they disagree.

## How to change a spec

1. Edit the relevant file. Mark the **0.1.0 / 0.2.0 / later** section you are changing.
2. Keep other specs consistent (especially `000-product.md` and `002-api.md`).
3. Do not implement a feature that is not in a spec.

## Index

| File | Topic |
|---|---|
| [000-product.md](000-product.md) | Goals, non-goals, versions, success |
| [001-architecture.md](001-architecture.md) | Rust/Kotlin split, UniFFI, process |
| [002-api.md](002-api.md) | Endpoints, DTOs, poll, clocks |
| [003-playback.md](003-playback.md) | Live stream, pause=stop, focus, FGS |
| [004-android-auto.md](004-android-auto.md) | Browse tree, no skip, session now-playing |
| [005-ui.md](005-ui.md) | Screens, `default-dark` tokens |
| [006-requests-faves.md](006-requests-faves.md) | 0.2.0 search / request / nick faves |
| [007-news.md](007-news.md) | 0.2.0 news |
| [008-settings.md](008-settings.md) | Prefs |
| [009-compat.md](009-compat.md) | API-level shims |
| [010-alarm-sleep-dj.md](010-alarm-sleep-dj.md) | 0.2.0 alarm, sleep, DJ notifier |
| [011-testing.md](011-testing.md) | Fixtures, unit, DHU |
| [012-build.md](012-build.md) | Nix, Gradle, NDK, semver |

## Versions

See `000-product.md`. App versions are **semver**, not the site’s `/v1/` path.
