# Snoozeloo design system

How the tokens in [`README.md`](./README.md) become Compose code. Decided in
[Design system: theme, typography, icons, splash (#14)](https://github.com/IgorJonski/Snoozeloo/issues/14)
on 2026-09-07; the deviations from the `kmp-compose-ui` skill (single scheme, custom
Switch, Material Symbols instead of Hugeicons) are recorded in
[ADR-0009](../adr/0009-design-system.md). Everything lives in
`:component:design-system:presentation` (ADR-0004) except the native splash, which is
host code.

Figma px map 1:1 to dp. Use sites read only `MaterialTheme.colorScheme.*`,
`MaterialTheme.typography.*` and `MaterialTheme.shapes.*`; the raw values below appear
in exactly one Kotlin file each.

## Colour scheme

One `lightColorScheme`, applied unconditionally: `SnoozelooTheme` never consults
`isSystemInDarkTheme()`, there is no `darkColorScheme`, and no extended-colour object.
Named values in `theme/Colors.kt` are named by hue, not role, because one value feeds
several slots:

| Name | Hex |
| --- | --- |
| `Blue` | `#4664FF` |
| `BlueLight` | `#ECEFFF` |
| `BlueMuted` | `#BCC6FF` |
| `White` | `#FFFFFF` |
| `Grey100` | `#F6F6F6` |
| `Grey200` | `#E6E6E6` |
| `Grey500` | `#858585` |
| `Grey900` | `#0D0F19` |

| Slot | Value | Used for |
| --- | --- | --- |
| `primary` / `onPrimary` | `Blue` / `White` | FAB, enabled Save, Turn off, selected Repeat-day chip, toggle "on" track, slider active track and thumb, entered time digits, Trigger time, Snooze border and text, back-button square, selected-ringtone circle, alarm icon on empty state and Trigger |
| `primaryContainer` / `onPrimaryContainer` | `BlueLight` / `Blue` | Snooze button background and its text; unselected Repeat-day chip background; slider inactive track |
| `inversePrimary` | `BlueMuted` | toggle "off" track |
| `secondary*`, `tertiary*` | copies of the `primary*` values | the palette has no second hue; default Material components stay on-brand |
| `background` / `onBackground` | `Grey100` / `Grey900` | every non-splash screen |
| `surface` / `onSurface` | `White` / `Grey900` | cards, toggle knob, dialog card; titles, card names, row labels, unselected chip label, empty-state text, Trigger name, ringtone names, typed text |
| `surfaceVariant`, `surfaceContainerLow` | `Grey100` | time-input field background, ringtone-row icon circle, back-arrow glyph |
| `onSurfaceVariant` | `Grey500` | "Alarm in …" countdown, Bedtime Hint, row values, empty `00` placeholder, the colon between time fields (always, even when the digits are blue) |
| `surfaceContainerHighest`, `outlineVariant` | `Grey200` | disabled Save background, Alarm-name field border, close-button square |
| `outline` | `Grey500` | nothing in Figma; set so no default Material outline shows up in an alien tone |
| `error`, `onError`, `errorContainer`, `onErrorContainer` | Material 3 baseline (`#B3261E`, `#FFFFFF`, `#F9DEDC`, `#410E0B`) | swipe-to-delete background and undo affordance (extended scope, absent from Figma) |
| `scrim` | `Grey100` | the Alarm-name dialog: Figma shows the settings content at 10 % opacity over `#F6F6F6`, i.e. the scrim is `scrim.copy(alpha = 0.9f)`, never a dark wash |

Resolved Figma inconsistencies: `#000000` (ringtone names, typed text) and `#28303F`
(the `Dark` variable on one bell stroke) collapse into `onSurface`; the colon stays
`onSurfaceVariant`; the unselected chip label uses `onSurface`, not
`onPrimaryContainer`. Whether Save is enabled is Alarm Settings ViewModel state (#17);
only its two colours are decided here.

## Typography

Montserrat Medium (500) and SemiBold (600) as two static TTFs in
`composeResources/font/` (`montserrat_medium.ttf`, `montserrat_semibold.ttf`), loaded
in `commonMain` with `Font(Res.font.montserrat_medium, FontWeight.Medium)` into one
`FontFamily`. The variable `Montserrat[wght].ttf` is not used. `OFL.txt` ships next to
the fonts in the repo and is listed in the app's third-party notices.

No `TextStyle` carries a colour; `lineHeight` is left unset (font metrics ≈ 1.22 ×
size, which is Figma "auto") except where noted.

| Slot | Weight | Size | Figma style(s) | Used for |
| --- | --- | --- | --- | --- |
| `displayLarge` | Medium | 82 | `displayTrigger` | Trigger-screen time |
| `displayMedium` | Medium | 52 | `displayInput` | hour and minute fields |
| `displaySmall` | Medium | 42 | `displayCard` | time on an alarm card |
| `headlineLarge` | Medium | 32 | — | derived, unused |
| `headlineMedium` | Medium | 28 | — | derived, unused |
| `headlineSmall` | Medium | 24 | `titleScreen`, `titleMeridiem` | "Your Alarms", `AM`/`PM` |
| `titleLarge` | SemiBold | 24 | `titleTrigger`, `buttonLarge` | Trigger alarm name (upper-cased at the use site), Turn off, Snooze |
| `titleMedium` | SemiBold | 16 | `titleCard`, `button` | card name, row labels, Save |
| `titleSmall` | SemiBold | 14 | `labelRingtone` | ringtone names |
| `bodyLarge` | Medium | 16 | `bodyEmpty` | empty-state message |
| `bodyMedium` | Medium | 14 | `bodySecondary`, `bodyInput` | countdown, Bedtime Hint, row values, typed text |
| `bodySmall` | Medium | 12 | — | derived, unused |
| `labelLarge` | SemiBold | 16 | `button` | same as `titleMedium`; Material `Button` reads this slot |
| `labelMedium` | Medium | 12, line height 16 | `labelChip` | Repeat-day chips |
| `labelSmall` | Medium | 11 | — | derived, unused |

## Shapes

`Shapes(extraSmall = 4.dp, small = 10.dp, medium = 10.dp, large = 30.dp, extraLarge = 30.dp)`.
`extraSmall` is the Alarm-name field, `small`/`medium` are cards, time fields and the
dialog, `large` is the Save / Turn off / Snooze pill. Circles (chips, FAB, toggle
tracks, ringtone icon circle, slider thumb) use `CircleShape` inside the component.
`small = medium` on purpose so `Card` and `TextField` defaults land on Figma without
overrides.

## Theme entry point

```kotlin
@Composable
fun SnoozelooTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = SnoozelooColorScheme, typography = SnoozelooTypography, shapes = SnoozelooShapes, content = content)
```

`App()` and `TriggerApp()` each wrap their `NavHost` in it (the iOS overlay draws
`TriggerApp()` over `App()`, so both roots need it). Every `@Preview` wraps in it too.

## Components

Split rule: the design system holds what knows nothing about alarms and exposes slots
or primitives; anything that takes an alarm UI model is a feature composable built
from these. Naming: the `Snoozeloo` prefix only where the composable shadows a
Material name; bare names where Material has no counterpart.

| Composable | Spec |
| --- | --- |
| `SnoozelooCard(modifier, content)` | `surface`, shape `medium`, padding 16, no elevation |
| `SnoozelooSwitch(checked, onCheckedChange, size: SwitchSize, modifier)` | custom-drawn (Material `Switch` is fixed at 52 × 32). `SwitchSize.Card`: track 51 × 30, padding 2, knob 26, shadow `0 3 7 rgba(0,0,0,.12)`. `SwitchSize.Row`: track 41 × 24, padding 2, knob 20, shadow `0 2.4 5.6 rgba(0,0,0,.12)`. Track `primary` on / `inversePrimary` off, knob `surface`. Knob offset animated and read in `offset { }`; `Modifier.toggleable(role = Role.Switch)` plus `minimumInteractiveComponentSize()` |
| `DayChip(label, selected, onClick, modifier)` | 38 × 26 pill, `labelMedium`; selected `primary`/`onPrimary`, unselected `primaryContainer`/`onSurface`; a plain `Box` with `selectable(role = Role.Checkbox)`, not `FilterChip` (its 32 dp minimum height and paddings miss Figma) |
| `PrimaryButton(text, onClick, enabled, modifier)` | `Button` with shape `large`, padding 16 h / 6 v (Save) or 32 h / 8 v (Turn off, via a `contentPadding` parameter); disabled = `surfaceContainerHighest` container with `onPrimary` label (not Material's 12 % alpha) |
| `SecondaryButton(text, onClick, modifier)` | `primaryContainer` background, 1 dp `primary` border, `primary` text, same shape and paddings as the large `PrimaryButton` (Snooze) |
| `SquareIconButton(icon, contentDescription, onClick, containerColor, contentColor, modifier)` | 32 dp rounded square; close = `surfaceContainerHighest` / `onPrimary`, back = `primary` / `surfaceVariant`; corner radius taken from `screens/alarm-settings.png` when built |
| `SettingRow(label, onClick?, modifier, trailing)` | height 52, padding 16, `space-between`; label `titleMedium` `onSurface`; trailing slot (a `bodyMedium` `onSurfaceVariant` value or a `SnoozelooSwitch(Row)`) |
| `VolumeSlider(value, onValueChange, modifier)` | Material `Slider` with a 6 dp track (`primary` active, `primaryContainer` inactive, shape `CircleShape`) and a 16 dp `primary` circle thumb via the `thumb`/`track` slots; 20 dp tall hit area |
| `TimeDigitField(value, onValueChange, modifier)` | 128 × 95, `surfaceVariant`, shape `medium`, `displayMedium` centred; placeholder `00` in `onSurfaceVariant`, entered digits `primary`; numeric keyboard, two characters; all validation in the ViewModel (#17) |
| `SnoozelooTextField(value, onValueChange, modifier)` | `BasicTextField`, `bodyMedium` `onSurface`, padding 12 h / 10 v, shape `extraSmall`, 1 dp `outlineVariant` border, `surface` background |
| `SnoozelooDialog(onDismiss, content)` | a full-screen `Box` drawn inside the screen (the name dialog is screen state, ADR-0004, not a route or a platform `Dialog`): `scrim.copy(alpha = 0.9f)` fill, card 328 wide at y = 223, `surface`, shape `medium`, padding 16 |
| `Fab(onClick, contentDescription, modifier)` | `FloatingActionButton` 60 dp, `CircleShape`, `primary`, elevation 4 dp (the list frame's shadow wins over the empty-state frame), `ic_plus` at 38 dp |

Feature-owned (not here): `AlarmCard`, `EmptyState`, `RepeatDaysRow`, `RingtoneRow`,
the Trigger screen layout. Design-system components take `contentDescription` /
`text` as parameters and own no string resources; the screen that resolves a string
owns it (`kmp-compose-ui` resource naming).

Spacing has no token object. Components carry their own sizes; screens use literal
`16.dp` for the horizontal padding and the gap between cards (10 dp on the ringtone
list).

## Icons

Google **Material Symbols** (Apache-2.0), exported as Android XML vector drawables into
`composeResources/drawable/`, drawn white (`#FFFFFF`) and tinted at the use site. The
Hugeicons glyphs in the Figma file are Pro styles; their exports were removed from the
repo (ADR-0009).

| Figma glyph | Material Symbol | File | Size at use |
| --- | --- | --- | --- |
| `solid/alarm` | `alarm`, FILL 1 | `ic_alarm_clock.xml` | 62 (empty state, Trigger), splash |
| `solid/plus` | `add` | `ic_plus.xml` | 38 (FAB) |
| `solid/remove-rectangle` cross | `close` | `ic_cross.xml` | inside the 32 dp `SquareIconButton` |
| `bulk/arrow-left-rectangle` arrow | `arrow_back` | `ic_arrow_left.xml` | inside the 32 dp `SquareIconButton` |
| `outline/notification-silent` | `notifications_off`, FILL 0 | `ic_bell_off.xml` | 18 |
| `outline/notification-ringing` | `notifications_active`, FILL 0 | `ic_bell_ringing.xml` | 18 |
| `checkmark-circle` glyph | `check` | `ic_check.xml` | 14.4 inside an 18 dp `primary` circle |

The squares behind close/back and the circles behind the check and the ringtone bells
are component backgrounds, not part of the icon. The colon between time fields is
text, not an icon (`docs/design/icons/colon.svg` shows it).

## Splash

**Android** (`:androidApp`, `androidx.core:core-splashscreen`, needed because minSdk 30
predates the system splash):

- `res/values/themes.xml`: `Theme.Snoozeloo` (parent `android:Theme.Material.Light.NoActionBar`, `android:windowBackground = @color/snoozeloo_background`) on `<application>` and on `AlarmTriggerActivity`; `Theme.Snoozeloo.Starting` (parent `Theme.SplashScreen`, `windowSplashScreenBackground = @color/snoozeloo_primary`, `windowSplashScreenAnimatedIcon = @drawable/ic_splash_alarm_clock`, `postSplashScreenTheme = @style/Theme.Snoozeloo`) on `MainActivity` only.
- `res/values/colors.xml` duplicates `#4664FF` and `#F6F6F6` for the host theme; this is the only place outside `Colors.kt` where a hex value appears.
- `res/drawable/ic_splash_alarm_clock.xml`: a 240 dp vector with the white `alarm` glyph about 96 dp wide, centred, because Android 12+ masks the icon to a 160 dp circle and ignores any other size; the 82 px icon on the Figma frame cannot be reproduced exactly.
- `MainActivity.onCreate`: `installSplashScreen()` before `super.onCreate()`, no `setKeepOnScreenCondition`.

**iOS** (`iosApp`): no storyboard. `Info.plist` gets a `UILaunchScreen` dictionary with
`UIColorName = SplashBackground` (a colour set `#4664FF` in `Assets.xcassets`),
`UIImageName = SplashAlarmClock` (an image set holding the same glyph as a single-scale
PDF with "Preserve Vector Data", 82 × 82 pt, white) and
`UIImageRespectsSafeAreaInsets = false`. AlarmKit intents start the process without UI,
so the launch screen never precedes the Trigger. Status-bar appearance on the launch
screen follows `UIStatusBarStyle` in `Info.plist` (light content on blue, as drawn in
Figma); in-app it follows the view controller.

The launcher / `AppIcon` artwork is out of scope for this map (the developer draws it
by hand later); until then the template placeholders stay.

## Edge-to-edge and insets

Both platforms draw edge-to-edge (AGP 9 / targetSdk 36 enforce it on Android). Every
screen sits in a `Scaffold` and lets its default `contentWindowInsets` pad the content;
the theme and the components know nothing about insets. The Trigger screen and the
splash draw under the status bar on purpose.

## Previews

Every component has a `@Preview` (`org.jetbrains.compose.ui.tooling.preview.Preview`)
in `commonMain` next to it, wrapped in `SnoozelooTheme`, showing its variants
(enabled / disabled, selected / unselected, both `SwitchSize`s). Previews are the
design system's only living documentation.

## Module layout

```
component/design-system/presentation/
  build.gradle.kts                      # snoozeloo.kmp.compose; material3, components-resources, ui-tooling-preview
  src/commonMain/kotlin/com/igoyon/snoozeloo/designsystem/
    theme/Colors.kt Typography.kt Shapes.kt Theme.kt
    component/<OneFilePerComposable>.kt  # with its @Preview
  src/commonMain/composeResources/
    font/montserrat_medium.ttf montserrat_semibold.ttf
    drawable/ic_alarm_clock.xml ic_plus.xml ic_cross.xml ic_arrow_left.xml ic_bell_off.xml ic_bell_ringing.xml ic_check.xml
```

Third-party notices to ship: Montserrat (SIL OFL 1.1), Material Symbols (Apache-2.0).
