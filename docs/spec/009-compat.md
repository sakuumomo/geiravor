# 009 — Compat

Package `io.r_a_d.geiravor.compat` inside `app`. Not a second Gradle module.

minSdk **26**. Call sites go through this package, not scattered `SDK_INT`.

## Marker

Every public function:

```
/** Compat: POST_NOTIFICATIONS. Remove when minSdk >= 33. */
```

Grep `Compat:` when raising minSdk and delete.

## 0.1.0 shims

| Concern | From API | Notes |
|---|---|---|
| `POST_NOTIFICATIONS` | 33 | Ask so the media notification can appear. Do **not** block play/pause if denied. |
| FGS `mediaPlayback` start | 34 | `startForeground` type |
| Edge-to-edge / insets | 35 | Status/nav bars |
| Display width (`WindowMetrics`) | 30 | Shade text area; `getRealSize` below 30 |

## 0.2.0

Exact alarm wrappers: `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` / `canScheduleExactAlarms`. If the user denies exact alarms, fail visible with Settings copy — not a silent skip.

MediaStore `RELATIVE_PATH` for saving a thread image (API 29).

## 0.3.0

Compose `Modifier.blur` / RenderEffect (API 31) for holiday glass. Below 31 the glass is the pack’s dark scrim only.

Coil GIF: `ImageDecoderDecoder` on API 28+, `GifDecoder` below. Remove the shim when minSdk ≥ 28.

`UiModeManager.setApplicationNightMode` (API 31) so Auto can follow Default vs Default light. Below 31 this is a no-op. Only call it when night vs day actually changes — a recreate must restore the current bottom tab.

## Not here

NDK 16 KB page size and AGP flags live in `012-build.md`, not Kotlin `SDK_INT` checks.
