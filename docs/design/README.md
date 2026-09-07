# Snoozeloo design tokens and assets

Raw material extracted from the Figma source of truth for the **Extended** variant:
[Snoozeloo (Copy)](https://www.figma.com/design/UJQ4zQnfKrThe6scJpS8fG/Snoozeloo--Copy-?node-id=62-6482),
page **UI**, section **Updates ✅** (node `62:6482`), row "Snoozeloo Extended version".
Extracted 2026-09-06 with the Figma MCP (`get_variable_defs`, `get_design_context`,
`download_assets`). Every value below is read from the file, not invented; where the
file is inconsistent the inconsistency is noted rather than resolved. How these become a Compose theme is decided in
[`design-system.md`](./design-system.md) (ADR-0009), not here.

Figma frames are **360 × 800** with a 52 px status bar and a 20 px home indicator;
all px values are Figma px and map 1:1 to dp.

## Colour palette

The file defines only one Figma variable, `Dark = #28303F`, and it is used solely as the
stroke of the outline bell icons. Every other colour is a raw fill.

| Token (proposed name) | Hex | Where it appears |
| --- | --- | --- |
| `primary` | `#4664FF` | Splash background; FAB; enabled Save button; Turn off button; selected Repeat-day chip; toggle "on" track; slider active track and thumb; time-input digits once entered; Trigger-screen time; Snooze button border and text; back-button square; ringtone "selected" check circle; alarm icon on empty state and Trigger |
| `primaryContainer` | `#ECEFFF` | Unselected Repeat-day chip; slider inactive track; Snooze button background |
| `primaryMuted` | `#BCC6FF` | Toggle "off" track (alarm card, Disabled alarm) |
| `background` | `#F6F6F6` | Screen background on every non-splash screen; time-input field background; ringtone-row icon circle; back-arrow glyph on the back button |
| `surface` | `#FFFFFF` | Cards; toggle knob; text and icons on `primary`; close-button glyph; Save label; splash alarm icon; check glyph |
| `onSurface` | `#0D0F19` | Screen titles, card titles, section labels, times and AM/PM on cards, unselected chip label, empty-state text, Trigger alarm name |
| `onSurfaceVariant` | `#858585` | "Alarm in …" countdown; Bedtime Hint; row values ("Work", "Default"); empty time-input placeholder `00`; colon between time fields |
| `disabled` | `#E6E6E6` | Disabled Save button background (label stays white); Alarm-name text field border; close-button square |
| `onSurfaceStrong` | `#000000` | Ringtone names in the Ringtone Setting list; typed text in the Alarm-name field |
| `iconStroke` (Figma variable `Dark`) | `#28303F` | Stroke of `notification-silent`; `notification-ringing` uses plain `black` |
| system home bar | `#1F1F1F` | Android home indicator (system UI, not app) |
| keyboard tray | `#E9EAEE` | Home-indicator strip while the IME is open (system UI, not app) |

Shadows and overlays:

| Use | Value |
| --- | --- |
| Toggle knob, 26 px (alarm card) | `0 3px 7px rgba(0,0,0,0.12)` |
| Toggle knob, 20 px (Vibrate row) | `0 2.4px 5.6px rgba(0,0,0,0.12)` |
| FAB | drop shadow `0 4px 2px rgba(0,0,0,0.25)` (present on the list screen, absent on the empty-state frame) |
| Alarm-name dialog scrim | the settings content behind the dialog is rendered at **10 % opacity** over `#F6F6F6`; no separate dark scrim layer exists in the file |

## Typography

Family: **Montserrat** everywhere in app content (the status bar uses Roboto, system UI).
Only two weights occur: **Medium (500)** and **SemiBold (600)**. Line height is Figma
"auto" unless stated (Montserrat auto ≈ 1.22 × size).

| Style (proposed name) | Weight | Size | Line height | Colour | Used for |
| --- | --- | --- | --- | --- | --- |
| `displayTrigger` | Medium | 82 | auto | `#4664FF` | Trigger-screen time `10:00` |
| `displayInput` | Medium | 52 | auto | `#858585` empty / `#4664FF` filled | Hour and minute fields in Alarm Settings |
| `displayCard` | Medium | 42 | auto | `#0D0F19` | Time on an alarm card |
| `titleScreen` | Medium | 24 | auto | `#0D0F19` | "Your Alarms" |
| `titleMeridiem` | Medium | 24 | auto | `#0D0F19` | `AM` / `PM` beside the card time (bottom-aligned, 4 px baseline offset) |
| `titleTrigger` | SemiBold | 24 | auto | `#0D0F19` | Alarm name on the Trigger screen, rendered upper-case (`WORK`) |
| `buttonLarge` | SemiBold | 24 | auto | white / `#4664FF` | Turn off, Snooze for 5 min |
| `titleCard` | SemiBold | 16 | auto | `#0D0F19` | Alarm name on a card; row labels (Alarm Name, Repeat, Alarm ringtone, Alarm volume, Vibrate) |
| `button` | SemiBold | 16 | auto | white | Save |
| `bodyEmpty` | Medium | 16 | auto | `#0D0F19` | Empty-state message, centred, two lines |
| `labelRingtone` | SemiBold | 14 | auto | `#000000` | Ringtone names |
| `bodySecondary` | Medium | 14 | auto | `#858585` | "Alarm in 30min", Bedtime Hint, row values |
| `bodyInput` | Medium | 14 | auto | `#000000` | Text typed in the Alarm-name field |
| `labelChip` | Medium | 12 | **16** | white / `#0D0F19` | Repeat-day chips `Mo … Su` |

## Spacing, sizes, radii

| Element | Values |
| --- | --- |
| Screen horizontal padding | 16 |
| Screen title top | 16 below the status bar (y = 68) |
| Content column | width 328; first card at y = 121 (list) or 124 (settings, ringtone) |
| Top bar (settings, ringtone) | 32 × 32 icon buttons at y = 68; Save is a pill `padding 16 h / 6 v`, radius 30 |
| Card | radius **10**, padding 16, white; vertical gap between cards 16 (list, settings) or 10 (ringtone list) |
| Alarm card internals | title → time gap 10; time → countdown gap 8; header row → chips gap 16; chips → Bedtime Hint gap 16; text column width 170 |
| Repeat-day chip | 38 × 26, pill (radius 38), padding 4 h / 6 v, 7 chips spread `space-between` across 296 (≈ 5 px gaps) |
| Toggle (alarm card) | track 51 × 30, radius 24, padding 2, knob 26 |
| Toggle (Vibrate row) | track 41 × 24, radius 19.2, padding 2, knob 20 |
| FAB | 60 × 60, radius 60, padding 12, `plus` icon 38, centred, bottom edge 50 above screen bottom (30 above the home indicator) |
| Time-input card | padding 24; two fields 128 × 95, radius 10, background `#F6F6F6`, padding 29 h / 16 v, 10 px gap around a 4 × 13.34 colon glyph |
| Time-input card (with countdown) | adds "Alarm in 7h 15min" (`bodySecondary`) 16 below the fields, centred |
| Settings row (label + value) | height 52, padding 16, `space-between` |
| Repeat / Volume sections | padding 16, label → control gap 10 |
| Volume slider | track height 6, radius 20, inactive `#ECEFFF`, active `#4664FF`; thumb 16 circle `#4664FF`; hit area 20 tall |
| Ringtone row | height 50, padding 16 h / 10 v; leading icon 18 inside a 30 circle (`#F6F6F6`, padding 6, radius 20); icon → label gap 6; trailing check 18 circle `#4664FF` with 14.4 glyph |
| Trigger screen | alarm icon 62; icon → time gap 24; time → name gap 10; text → buttons gap 24; buttons 271 wide, padding 32 h / 8 v, radius 30, gap 10; Snooze has a 1 px `#4664FF` border |
| Empty state | alarm icon 62, icon → text gap 32, block centred on screen |
| Alarm-name dialog | card at y = 223, width 328, padding 16, radius 10; label → field gap 10; field padding 12 h / 10 v, radius **4**, 1 px `#E6E6E6` border; Save pill right-aligned 10 below |
| Splash | full-bleed `#4664FF`; alarm icon 82 × 82 (white) centred; status bar and home indicator drawn white |

## Icons

All icons in the file are "Huge-icon" instances (Hugeicons). The `solid` and `bulk`
styles used here are **Hugeicons Pro**, so their SVG exports are not kept in this public
repository; the app ships Material Symbols instead (ADR-0009,
[`design-system.md`](./design-system.md)). The table records what the file shows so the
Material replacements can be matched by size and role.

| Figma component | Frame size | Glyph box | Colour in the file | Used on |
| --- | --- | --- | --- | --- |
| `Huge-icon/interface/solid/alarm` | 62 | 55.54 (inset 5.21 %) | `#4664FF` | Empty state, Trigger; white at 82 × 82 (glyph 73.46) on the splash |
| `Huge-icon/interface/solid/plus` | 38 | 21.375 (inset 21.88 %) | white | FAB |
| `Huge-icon/interface/solid/remove-rectangle` | 32 | 32 | square `#E6E6E6`, cross white | Settings top bar (close / discard) |
| `Huge-icon/arrows/bulk/arrow-left-rectangle` | 32 | 32 | square `#4664FF`, arrow `#F6F6F6` | Ringtone Setting top bar |
| `Huge-icon/interface/outline/notification-silent` | 18 | 18 | stroke `#28303F` | Silent row |
| `Huge-icon/interface/outline/notification-ringing` | 18 | 18 | stroke black | Every non-silent ringtone row |
| `Interface, Essential/checkmark-circle-1` (glyph only) | 14.4 in an 18 circle | 23.05 (overflows 30 %) | stroke white 1.44 | Selected ringtone |

Two plain vectors that are not Hugeicons are kept as exported:

| File | Size | Colour | Used on |
| --- | --- | --- | --- |
| `icons/colon.svg` | 4 × 13.34 | `#858585` | Between hour and minute fields (same grey even when digits are blue) |
| `icons/slider-thumb.svg` | 16 circle | `#4664FF` | Volume slider thumb (a drawn circle is enough) |

Not exported on purpose: status-bar wifi/signal/battery and the Android keyboard
mock-ups (system UI), and the toggle knob (a plain rounded rectangle).

## Fonts

`fonts/Montserrat-Medium.ttf` and `fonts/Montserrat-SemiBold.ttf` are the static
instances from the upstream repository
[JulietaUla/Montserrat](https://github.com/JulietaUla/Montserrat) (`fonts/ttf/`),
licensed under the **SIL Open Font License 1.1** (`fonts/OFL.txt`). Google Fonts ships
the same family as a variable font (`Montserrat[wght].ttf`) if a single file is
preferred later.

## Reference renders

`screens/alarm-list.png` and `screens/alarm-settings.png` are Figma exports at 1× of the
Extended list and settings frames; `splash/splash-360x800.png` is the splash frame
(the only record of the splash bell, the Pro SVG was not kept).
They are for eyeballing, not pixel diffing.

## Licensing notes

- Montserrat: OFL 1.1, bundling in the app is fine; keep `OFL.txt` next to the fonts.
- Hugeicons: the glyphs in the Figma file are **Pro** styles (`solid`, `bulk`); only
  the Stroke Rounded set is free (MIT). Their SVG exports were removed from the repo and
  must not be re-added; the app ships Material Symbols instead. Decided in
  [ADR-0009](../adr/0009-design-system.md); see
  [`design-system.md`](./design-system.md) for the glyph-by-glyph mapping.

## Inconsistencies found in the file

- The alarm-list frame's FAB carries a drop shadow; the empty-state frame's FAB has none.
- The Trigger button reads "Turn Off" in the MVP row and "Turn off" in the Extended row.
- The colon between the time fields stays `#858585` when the digits turn `#4664FF`.
- `notification-silent` strokes with the `Dark` variable (`#28303F`) while
  `notification-ringing` strokes plain black.
- The Save button in the Extended settings frame `62:4336` is enabled (`#4664FF`) while
  the same frame at `62:4191` (empty `00:00`) shows it disabled (`#E6E6E6`).
