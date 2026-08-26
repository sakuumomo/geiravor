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

| Key | Notes |
|---|---|
| Faves nick | string, optional |
| Alarm / snooze / sleep | `010-alarm-sleep-dj.md` |
| DJ notifier opt-in | default off |

Theme stays `default-dark` until a later spec.
