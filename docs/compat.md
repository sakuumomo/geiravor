# Compat

Package `io.r_a_d.geiravor.compat` inside `app` (when the shell exists). Not a second Gradle module.

minSdk **26**. Call sites go through this package, not scattered `SDK_INT`.

## Marker

Every public function:

```
/** Compat: POST_NOTIFICATIONS. Remove when minSdk >= 33. */
```

Grep `Compat:` when raising minSdk and delete the **version gate**. Runtime permissions that still exist after minSdk catch-up keep a named helper.

## Gates

| Concern | From API | After minSdk catch-up |
|---|---|---|
| Coil GIF decoder | 28 | Delete shim (only 26–27 today) |
| MediaStore `RELATIVE_PATH` (thread image save) | 29 | Delete shim |
| Display width (`WindowMetrics`) | 30 | Delete shim |
| Compose `Modifier.blur` / RenderEffect (holiday glass) | 31 | Below 31: pack scrim only, no blur |
| `UiModeManager.setApplicationNightMode` (Auto dark/light) | 31 | Below 31: no-op. Only call when night vs day actually changes — a recreate must restore the current bottom tab |
| Exact alarms `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 31 / 33 | Keep a named helper; if denied, fail visible with Settings copy — not a silent skip |
| `POST_NOTIFICATIONS` | 33 | Keep **requesting** the permission. Playback does not wait on grant. Only `if (SDK_INT >= 33)` dies when minSdk ≥ 33 |
| FGS `mediaPlayback` start type | 34 (typed FGS from 29) | Always pass the type; marker must match the real branch |
| Edge-to-edge / insets | 35 | Always enable |

## Not here

NDK 16 KB page size and AGP flags live in [build.md](build.md), not Kotlin `SDK_INT` checks.
