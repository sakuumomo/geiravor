# 008 — Settings

Persist with DataStore (not `SharedPreferences` directly).

## 0.1.0

| Key | Type | Default | UI |
|---|---|---|---|
| Player gain | float 0–1 | **0.8** (site volume 80) | Now playing slider 0–100 |
| Auto-start on plug | bool | **false** | Settings → General (wired headset) |
| Auto-start in vehicle | bool | **false** | Settings → Auto (Android Auto / car mode; USB or wireless) |

Show app `versionName` (semver) in Settings → General, plus the project name **Geiravor** and that it is based on r/a/dio’s Valkyrie.

Android Auto Settings tab surfaces the same two auto-start flags (On/Off subtitle) and About. Tapping an auto-start row flips that flag without playing the stream. About is display-only. Phone Settings → General / Auto still own the same DataStore keys; a switch must turn **off** as well as on.

## 0.2.0

| Key | Storage | Notes |
|---|---|---|
| Favorites nick | DataStore | Public list only (`GET /faves?nick=`) |
| Connection nick | DataStore | IRC fave/unfave nick; empty → Favorites nick |
| NickServ password | EncryptedSharedPreferences | Direct Rizon only when SASL was not used; never log |
| IRC profile | DataStore | Rizon (default) vs bouncer |
| Bouncer host / port | DataStore | Port default 6697 |
| Bouncer `PASS` | EncryptedSharedPreferences | Opaque; never log |
| Allow insecure TLS | DataStore bool, default **false** | Bouncer IRC only |
| TLS fingerprint | DataStore | Server cert SHA-256; empty = unpinned |
| SASL username | DataStore | Empty → public nick |
| SASL password | EncryptedSharedPreferences | PLAIN when no client cert PEM; never log; no copy/cut |
| Client cert PEM | EncryptedSharedPreferences | TLS client cert; may include the key; never log; copy allowed; Clear with confirm |
| Client key PEM | EncryptedSharedPreferences | Optional if the cert blob already has a key; never log; no copy/cut; Clear with confirm |
| Alarm / snooze / sleep | DataStore | `010-alarm-sleep-dj.md` |
| DJ notifier opt-in | DataStore | default off |

`008` still prefers DataStore over SharedPreferences. **Exception:** secrets only may use AndroidX Security EncryptedSharedPreferences (Tink). Do not move gain / auto-start into it.

Settings sections are **General | Auto | Connection**. Alarm / snooze / sleep / DJ notifier live under General unless they overflow — then an **Alerts** section tab, still not a fifth bottom tab. IRC connection (nick, profile, NickServ, bouncer host/port/`PASS`, Allow insecure TLS, TLS fingerprint, SASL, client PEM, Test connection) is **Settings → Connection**. The Favorites tab nick stays the public list. NickServ / bouncer `PASS` / SASL password have no copy/cut.

## 0.3.0

| Key | Storage | Default | UI |
|---|---|---|---|
| Theme pack | DataStore | `default-dark` | Settings → General: **Default** \| **Default light** \| **Christmas** \| **Halloween** \| **New Years** |
| Opt out of holiday themes | DataStore bool | **false** (auto allowed) | Settings → General (auto only) |
| Convert schedule times to local | DataStore bool | **false** | Settings → General |

Theme pick may be any of the five packs, any day. Holiday **auto** still applies when the live site is serving them, opt-out is off, and a holiday window is open (`005-ui.md`). Opt-out does not block a manual holiday pick. Auto follows Default / Default light (holiday user pick or holiday auto → Default / night). Application night mode is set from that same dark/light split (`004-android-auto.md`).

Schedule local-time rewrite uses zone `America/New_York` as the source (`013-schedule-staff.md`). Toggle off shows the stored body.
