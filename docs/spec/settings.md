# Settings

Persist prefs. Secrets use encrypted storage. Do not put gain / auto-start in the secrets store.

Phone sections: **General | Auto | Connection**. Alarm / snooze / sleep / DJ notifier / fave-currently-playing live under General unless they overflow — then an **Alerts** section tab, still not a fifth bottom tab.

## Keys

| Key | Storage | Default | UI |
|---|---|---|---|
| Player gain | prefs | **0.8** (site volume 80) | Now playing slider 0–100; same gain on Auto |
| Auto-start on plug | prefs | **false** | Settings → General (wired headset); Auto browse Settings |
| Auto-start in vehicle | prefs | **false** | Settings → Auto (Android Auto / car mode); Auto browse Settings |
| Favorites nick | prefs | empty | Favorites tab; public list (`GET /faves?nick=`); empty → Connection nick |
| Connection nick | prefs | empty | Settings → Connection; IRC fave/unfave; empty → Favorites nick |
| NickServ password | encrypted | empty | Direct Rizon only when SASL was not used; never log; no copy/cut |
| IRC profile | prefs | Rizon | Rizon vs bouncer |
| Bouncer host / port | prefs | port **6697** | Bouncer; Host required `*` |
| Bouncer `PASS` | encrypted | empty | Opaque; never log; no copy/cut |
| Allow insecure TLS | prefs | **false** | Bouncer IRC only |
| TLS fingerprint | prefs | empty | Server cert SHA-256; empty = unpinned |
| SASL username | prefs | empty | Empty → public nick |
| SASL password | encrypted | empty | PLAIN when no client cert PEM; never log; no copy/cut |
| Client cert PEM | encrypted | empty | TLS client cert; may include the key; never log; copy allowed; Clear with confirm |
| Client key PEM | encrypted | empty | Optional if the cert blob already has a key; never log; no copy/cut; Clear with confirm |
| Theme pack | prefs | `default-dark` | Settings → General: **Default** \| **Default light** \| **Christmas** \| **Halloween** \| **New Years** |
| Opt out of holiday themes | prefs | **false** (auto allowed) | Settings → General (auto only) |
| Convert schedule times to local | prefs | **false** | Settings → General |
| Alarm / snooze / sleep | prefs | [alarm-sleep-dj.md](alarm-sleep-dj.md) | Settings → Alerts |
| DJ notifier opt-in | prefs | **false** | Settings → Alerts |
| Fave currently playing | prefs | **false** | Settings → Alerts |

Show app `versionName` (semver) in Settings → General, plus the project name **Geiravor** and that it is based on r/a/dio’s Valkyrie.

Android Auto Settings tab surfaces the same two auto-start flags (On/Off subtitle) and About. Tapping an auto-start row flips that flag without playing the stream. About is display-only. A switch must turn **off** as well as on.

## Connection labels

Required fields are marked `*` on the label: Rizon is **Nick** only; Bouncer is **Nick** and **Host** (port still defaults to 6697). Empty Connection nick still falls back to the Favorites nick. The Favorites tab nick stays the public list.

## Themes

Theme pick may be any of the five packs, any day. Holiday **auto** still applies when the live site is serving them, opt-out is off, and a holiday window is open ([ui.md](ui.md)). Opt-out does not block a manual holiday pick. Auto follows Default / Default light (holiday user pick or holiday auto → Default / night). Application night mode is set from that same dark/light split ([android-auto.md](android-auto.md)). Changing theme (or opt-out) stays on Settings.

Schedule local-time rewrite uses zone `America/New_York` as the source ([schedule-staff.md](schedule-staff.md)). Toggle off shows the stored body.
