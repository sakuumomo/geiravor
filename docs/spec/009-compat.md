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

## 0.2.0

Exact alarm / `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` wrappers for the alarm clock.

## Not here

NDK 16 KB page size and AGP flags live in `012-build.md`, not Kotlin `SDK_INT` checks.
