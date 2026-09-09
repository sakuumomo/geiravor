# Schedule and staff

HTML only. No JSON. Not in Android Auto. Parse in the domain crate (news-list style: look for known markers, not a general HTML client, not a WebView).

Constants (domain, not UI literals): `https://r-a-d.io/schedule`, `https://r-a-d.io/staff`, weekdays Monday-first, staff role order `staff` \| `dev` \| `dj`, source zone `America/New_York`.

## Fetch

GET **when that Board section is opened** (lazy), then revalidate on open. Not on a timer. Show cache first; write only if the payload changed. If it changed, update the screen; if it is the same, do not write and do not flash loading.

Theme name on this HTML (`/assets/{name}/css/`) is reused for holiday sniff before a dedicated `GET /` ([api.md](api.md), [ui.md](ui.md)).

## Schedule

`GET https://r-a-d.io/schedule`. Seven rows, **Monday first** (Valkyrie `ScheduleDay`: Monday=0 … Sunday=6). The site emits each day twice (desktop `is-hidden-touch` and mobile `is-hidden-desktop`); keep **one** copy per weekday.

Each row:

| Field | Source |
|---|---|
| Weekday | `data-weekday` on `.schedule-text` (`Monday` … `Sunday`) |
| Body | `.schedule-text` inner text (decode entities; collapse whitespace) |
| Owner name | the `h6` in that cell that is **not** the weekday |
| Owner image | `img.dj-image` `src` in that cell → `https://r-a-d.io/api/dj-image/{file}` |

Empty owner **and** empty/whitespace body → empty day. Do **not** substitute Hanyuu. Missing image → no image (not mystery-DJ), but the row still **reserves the image slot** so text lines up with days that have a portrait. Text without an owner is still shown (open-slot days). Image may sit before or after that day’s `.schedule-text`; take it from **that day’s `.cell` only** (desktop copy), never from the next weekday. Highlight **today** using the device-local weekday (type **and** the row border).

No bio, no tap-through. Ignore the site’s per-slot `Notification` flag.

### Local times

Site banner: times are EST. Source zone is **`America/New_York`** (DST via that zone, not a fixed −5).

Settings → General **Convert schedule times to local** (default **off**, [settings.md](settings.md)). When off, show the stored body. When on, rewrite **unambiguous** clock tokens **at paint** (disk keeps the original). Convert **in place** — do not append “for You”.

Unambiguous tokens (optional trailing `EST`/`EDT`, any case):

- `\d{1,2}\s*[AaPp][Mm]` — `8pm`, `8 pm`, `2PM`
- `\d{1,2}:\d{2}\s*[AaPp][Mm]` — `11:59 pm`
- `\d{1,2}:\d{2}` with hour 0–23 — `22:00`, `18:00`, `8:00`

Not tokens: bare integers, dates `YYYY-MM-DD`, URLs, `>>id`. Leave unmatched text alone.

Use the row weekday as the ET calendar day (today if missing). Keep 12h vs 24h and am/pm casing of the original. Drop a trailing EST/EDT (now local). If the local calendar day differs, append that weekday (`5pm Wednesday`).

## Staff

`GET https://r-a-d.io/staff`. Active users only (server already filters). Groups in order **staff | dev | dj** from `#staff`, `#dev`, `#dj`. Display labels **Staff**, **Developers**, **DJs**. Show a heading even if that group has no cards.

Each card: `img.dj-image` `src` + `.dj-card-name` text. Same loader as DJ image ([api.md](api.md)), including GIF autoplay and muted looping mp4/webm. Name colors: staff green, dj **blue** (not the Halloween/New Years play accent), dev red. **No bio** — that text belongs on the schedule.

Layout follows the live staff page, sized for a phone/tablet:

- **Staff** and **Developers** sit **side by side** with a vertical separator from **600dp** (tablet / two-pane). Phone stacks them. Each of those groups is two-across (live `#notdjs` / `has-2-cols`).
- **DJs** are **four-across** from 600dp, **two-across** on phone (live `has-4-cols has-2-cols-mobile`). A short last row keeps leftover cards together and does **not** stretch them across the row.
- Square images with the same 6dp corner as cards. Group titles **Staff** / **Developers** / **DJs** are centered over the group. A 2dp rule under each group title (`content-border-top`).
