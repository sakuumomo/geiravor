# Product spec

These files are **normative** for what a listener gets. Code implements them. Live [r-a-d.io](https://r-a-d.io) and `GET https://r-a-d.io/api` beat any example in this tree when they disagree.

How this tree is built, tested, and split is **repo** documentation (`docs/README.md`), not this folder. Version history is [`CHANGELOG.md`](../../CHANGELOG.md), not sections in these files.

## How to change a spec

1. Edit the relevant file. Do not add `0.x` / `1.0.0` feature buckets.
2. Keep other product specs consistent (especially `product.md` and `api.md`).
3. Do not implement a feature that is not in a spec.

## Index

| File | Topic |
|---|---|
| [product.md](product.md) | Goal, non-goals, identity, success |
| [api.md](api.md) | Endpoints, snapshot, poll, clocks, least network |
| [playback.md](playback.md) | Live stream, pause=stop, focus, notification |
| [android-auto.md](android-auto.md) | Browse tree, no skip, session now-playing |
| [ui.md](ui.md) | Screens, chrome, theme packs |
| [requests-faves.md](requests-faves.md) | Search, request, nick favorites (list HTTP, add IRC) |
| [news.md](news.md) | News list, article, comments |
| [schedule-staff.md](schedule-staff.md) | Schedule and staff HTML |
| [settings.md](settings.md) | Prefs the listener can set |
| [alarm-sleep-dj.md](alarm-sleep-dj.md) | Alarm, sleep, DJ and fave-on-air notices |
