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
| Favorites nick | DataStore | Public list + direct-Rizon `NICK`. Must match the Rizon nick Hanyuu sees |
| NickServ password | EncryptedSharedPreferences | Direct Rizon only; never log |
| IRC profile | DataStore | Rizon (default) vs bouncer |
| Bouncer host / port | DataStore | Port default 6697 |
| Bouncer `PASS` | EncryptedSharedPreferences | Opaque; never log |
| Allow insecure TLS | DataStore bool, default **false** | Bouncer IRC only |
| Alarm / snooze / sleep | DataStore | `010-alarm-sleep-dj.md` |
| DJ notifier opt-in | DataStore | default off |

`008` still prefers DataStore over SharedPreferences. **Exception:** secrets only may use AndroidX Security EncryptedSharedPreferences (Tink). Do not move gain / auto-start into it.

Settings sections stay **General | Auto**. Alarm / snooze / sleep / DJ notifier live under General unless they overflow — then an **Alerts** section tab, still not a fifth bottom tab. Favorites connection lives on Songs → Favorites (`006-requests-faves.md`), not a Favorites settings tab.

Theme stays `default-dark` until a later spec.
