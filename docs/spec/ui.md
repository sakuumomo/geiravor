# UI

Compose, one Activity, not XML Views, not a WebView of the site.

## Screens

Phone **bottom tabs** (four): **Now Playing | Songs | Board | Settings** (Settings last).

Two-pane only when **width ≥ 840dp and smallest width ≥ 600dp** (tablet / wide). 16:9 phones stay single-pane in landscape. Two-pane: **Now Playing is always the left pane.** Bottom tabs are **Songs | Board | Settings** and switch only the right pane (default Songs). There is no Now Playing tab when the left pane is already showing it. Thread stays phone-only (not the tablet pane).

If **Now Playing** / **Last Played** do not fit one line: **two-line labels**, no hardcoded character cap. Tabs (and their icons/labels) are **vertically centered** so **Songs** sits at mid-height of **Now Playing**.

UI noun is **Favorites**. Verb / Auto command is **Fave**. Do not label anything **Faves**.

Songs, Board, and Settings use **section tabs** at every width.

| Destination | Content |
|---|---|
| Now Playing | Play/stop, volume 0–100 (default 80), **Fave** (heart), title/artist, collapsible tags, progress + mm:ss / mm:ss (AFK only; live DJ hides the bar), listeners (centered), DJ image + `dj.djname`, **Next** then **Last played** (centered; live DJ next is `???`), then thread if present. Toggle the heart **immediately** on tap; revert if the attempt fails or is a no-op. One in-flight fave. Failures also as red text, faded out when `np` changes |
| Songs | **Last Played \| Queue \| Request \| Favorites**. Queue hidden when not AFK. `/r/` + blue on `type == 1`. Request is search + request. Favorites is nick + public list + per-row Request when AFK + **Request random**. Request and Favorites use the same no-scroll pane pager as news |
| Board | **News \| Schedule \| Staff** (default News). News: list + article. Schedule: seven weekday rows, Monday first. Staff: groups Staff / Developers / DJs; image + name; no bio |
| Settings | **General \| Auto \| Connection**. Overflow **Alerts** (alarm / snooze / sleep / DJ notifier / fave currently playing) — not a fifth bottom tab. General: auto-start on plug, about, theme pick, holiday opt-out, convert schedule times to local. Auto: auto-start in vehicle. Connection: Rizon vs Bouncer ([requests-faves.md](requests-faves.md), [settings.md](settings.md)) |

URLs, weekday order, staff role order, holiday windows, EST zone id (`America/New_York`), and Board section labels live in domain constants, not string literals in UI.

## Layout

Follow the live homepage order for player chrome: play + volume, title, tags (+/-), progress + listeners + clock, DJ column, next, previous, then thread.

Now Playing lists **Next** first (`queue[0]` when AFK; live DJ: `???`) and **Last played** second (`lp[0]`). Currently playing may wrap up to two lines. Next and last played are one line each, centered, ellipsized to the width of the screen. Hug panes sit at the **top** of the tab (wallpaper still shows below). DJ name is centered under the image. **Listeners** is centered on its line; the AFK mm:ss clock stays on the right of that line. Full last-played and queue live on the Songs tab.

Tags: one space-separated line from `tags[]`, collapsed behind +/- like the site.

Times: relative from `timestamp` vs `current` ([api.md](api.md)).

Thread: phone only, **below** next/previous. Hidden on Hanyuu. Live DJ: embed `image:` / image URLs (long-press save or open); other URLs open the browser. `.gif` / `.mp4` / `.webm` on DJ image, schedule/staff cards, and thread embed autoplay; loop when that is the format default (GIF loop count, video loops). Still images stay still. Videos play muted. News article images do **not** autoplay.

## Theme packs

The current pack is committed Compose tokens, not loose HSL in call sites. Copy/update them by hand from live `/assets/{theme}/css/` (and `main.css` + images) when a pack is added or the site moves. Do **not** download or parse CSS at runtime. Do **not** WebView the site.

Holiday **wallpapers** are committed under `drawable-nodpi/` (`wallpaper_{christmas,halloween,newyears}.jpg`), scaled from live `/assets/{theme}/images/`. Phone only; Auto never paints them.

Not the old app’s orange `#DF4C3A`. Material 3 is re-skinned to these tokens.

| Pack | From live | Surface | Play/accent (`--edenlight-color`) | Wallpaper |
|---|---|---|---|---|
| `default-dark` | `--radio-secondary-1` hsl(0,0%,11%) bg; `--radio-primary-1` 13% cards; `--radio-primary-3` 24% borders; `--text-secondary` 96% text; `--text-primary` 50% muted; `--blue` hsl(208,27%,39%); `--red` 348/27%/50%; `--green` 153/27%/39%; `--text-link` 208/27%/50% | dark cards | hsl(208, 27%, 39%) | none |
| `default-light` | stock Bulma light (no `--radio-*`): scheme 221/14%, text 21%/48%, info 198, link 233 | off-white page, white cards | hsl(198, 100%, 41%) | none |
| `christmas` | edenlight + `#38A8E4` | **white** cards, dark card text, white page text | `#38A8E4` | `christmas.jpg` |
| `halloween` | edenlight + `#F4A246` + dark glass | **50% black** cards (`rgba(0,0,0,0.5)`), white text | `#F4A246` | `halloween.png` |
| `newyears` | edenlight + `#60709f` + dark glass | **50% black** cards (`rgba(0,0,0,0.5)`), white text | `#60709f` | `newyears.webp`/`png` |

Play control uses that pack’s accent (`is-info` equivalent on default-dark).

Christmas snow and Halloween hair/moon/pumpkins are light; type is not painted on the wallpaper. A pane owns a **gutter** (16.dp around the pane when there is a wallpaper; none on Default) and **inner pad** (16.dp inside the pane on every pack). Nested cards / chrome do **not** stack a second blur+scrim on that pane. Section tabs over wallpaper use the same frost/sheet. Unselected pager chips are a raised fill, not the same `surface` as the pane (or they vanish on glass). Glass matches live `.box` / `.glass`: 50% black + 15.dp blur, one layer. The frost copy is the **laid-out** page wallpaper (same crop as the background), not the jpg’s intrinsic 1920×1080 — that landscape bitmap only covers the top half of a portrait pane and looks like a second wallpaper. Alert dialogs on glass are an opaque sheet (no frost).

Panes **hug their chrome** when the body is short (Now Playing play-through-next/prev, Last Played / Queue, schedule, staff) so holiday wallpaper stays visible below. Paginated lists (Request, Favorites, news list) and the news article still **fill** the tab — they measure row count from that height. Now Playing **logo** sits in a compact wrap-content badge (not full-width; glass/sheet only when there is a wallpaper). Type on panes/cards is **card** `text` / `muted`, not `onBackground` (Christmas on-wallpaper type is white; pane type is dark). Article **← News** is still a pinned link over the body (not a toolbar); a compact **film** chip (glass fill / Christmas white sheet / Default `surface`) sits behind the label so hover and type read on wallpaper.

Play, volume, and request keep that pack’s `--edenlight-color`. Selection chrome (theme radios, pager current, tab/nav indicator, switches, schedule today) uses **highlight**: the same hue, lifted toward white on glass packs until luma is high enough to read on wallpaper (Halloween `#F4A246` and New Years `#60709f` especially). Glass selection wash is nearly opaque. **Hover** on clickable rows (Settings theme radios with a pointer) uses that same highlight at a stronger alpha than Material’s 8% — 8% white on Halloween/New Years glass is invisible. Non-glass packs keep the accent hue and default hover.

Do **not** invert default-dark to make default-light. Do **not** collapse holidays to one palette.

| Pack | When |
|---|---|
| `default-dark` | User default |
| `default-light` | User picker |
| `christmas` / `halloween` / `newyears` | User picker **any day**, and auto when the **live site** is serving that theme |

Settings → General: Theme **Default** | **Default light** | **Christmas** | **Halloween** | **New Years**. **Opt out of holiday themes** (default **off** = auto allowed) only blocks the **automatic** switch; a manual holiday pick still applies. Changing theme (or opt-out) stays on Settings; it must not send the user back to Songs / Now Playing.

Holiday detection: the site turns holidays on when an admin sets `HolidayTheme`, not by calendar. Geiravor only **looks** during these **device-local** windows:

| Pack | Window (inclusive) |
|---|---|
| Halloween | 29 October – 1 November |
| Christmas | 1–26 December |
| New Years | 27 December – 3 January |

Outside those windows: never sniff, never auto-apply a holiday pack. Inside: sniff on **process start**. If the site is **not** on that holiday yet, at most **once per hour** until it is. **Once seen on** (`christmas` / `halloween` / `newyears` on the HTML), **stop sniffing until that window ends**. App start may sniff again. Reuse HTML already in hand (news/schedule/staff) before `GET /`. Not every `/api` poll.

If the public theme is one of the three holidays and the user has **not** opted out, apply that pack (overrides the user pick until the window ends). Otherwise use the user pick, which may itself be a holiday pack. Do **not** follow `dj.theme_id` / `dj.css`. Other public names (`suzu`, `tuicss`, `edenlight`) are ignored. Holiday packs (manual or auto) are **phone-only**; Auto follows Default / Default light (a holiday user pick or holiday auto maps to Default). Application night mode is Default light → day, everything else → night ([android-auto.md](android-auto.md)).

Theme **name** is the path segment in `href="/assets/{name}/css/…"` (first stylesheet).

## Assets (committed)

From `https://r-a-d.io/assets/images/` (and mystery-DJ from the site’s `mystery-dj.png` if present, else a committed placeholder):

- `logo_image_small`
- `logotitle_2`
- launcher mipmaps
- mystery-DJ fallback — site `mystery-dj.png`; committed copy `drawable-nodpi/mystery_dj.jpg`
- holiday wallpapers — `drawable-nodpi/wallpaper_{christmas,halloween,newyears}.jpg` from live `/assets/{theme}/images/` (scaled; do not fetch at runtime)

Gradle must not read files outside this repo.

Launcher label: `r/a/dio`.

## Edge-to-edge

Use compat. Content draws behind system bars with insets padding.
