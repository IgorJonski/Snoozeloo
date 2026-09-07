# Screen spec: Ringtone Setting

Decided in [Screen spec: Ringtone Setting (#18)](https://github.com/IgorJonski/Snoozeloo/issues/18) on 2026-09-07. Inputs: the Figma ringtone frame as recorded in `docs/design/README.md` (the frame itself was not re-read: the Figma MCP budget is exhausted), the PDF "Ringtone Setting Screen" bullet list (list all default Android ringtones, include Silent, tap = select + short preview, **do not navigate back on selection**), ADR-0004 (route `RootRoute.RingtoneSetting(ringtoneId: String?)`, the result through `previousBackStackEntry.savedStateHandle[RingtoneSettingResult.KEY]`), ADR-0008 (`RingtoneId`, `RingtoneCatalog`, `RingtonePreviewPlayer`, the Preview stop rules, Default plays nothing on iOS), ADR-0009 (`SquareIconButton`, `SnoozelooCard`, `ic_bell_off`, `ic_bell_ringing`, `ic_check`, `ic_arrow_left`; `RingtoneRow` is feature-owned) and `docs/specs/alarm-settings.md` (how the result is consumed). Conventions: `kmp-presentation-mvi` (State/Action/Event, Root/Screen split, semantic types mapped to strings in the UI, `SavedStateHandle` for process death), `kmp-navigation` (route arguments read in the ViewModel), `kmp-error-handling` (use cases return `Result`, every failure reported once) and `kmp-compose-ui`. Glossary terms are `CONTEXT.md`'s (Ringtone, Silent, Default Ringtone, Preview).

The screen lives in `:feature:alarms:presentation` as `RingtoneSettingScreen.kt` (`RingtoneSettingRoot` above `RingtoneSettingScreen`), driven by `RingtoneSettingViewModel`. It is reached only from Alarm Settings, with the draft's ringtone id encoded in the route, and it never pops itself: the user leaves with the back button, the system back or the iOS back swipe.

## Layout

`Scaffold` on `background`. The top bar is fixed: `SquareIconButton(ic_arrow_left, containerColor = primary, contentColor = surfaceVariant)` (back) at the leading edge, 16 dp below the status bar, 16 dp horizontal padding; there is no title (Figma: only the 32 dp button at y = 68). Below it a `LazyColumn` starting 24 dp under the bar (Figma: first row at y = 124), 16 dp horizontal padding, 10 dp between rows, bottom `contentPadding` 32 dp so the last row clears the home indicator, `key = ringtone.id.encode()`, `Modifier.selectableGroup()`.

### `RingtoneRow` (feature-owned)

`SnoozelooCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp))`, height 50 dp, `Modifier.selectable(selected, role = Role.RadioButton, onClick)`, a `Row` with `verticalAlignment = CenterVertically`:

| Slot | Content |
| --- | --- |
| Leading | a 30 dp `surfaceVariant` `CircleShape` box holding, at 18 dp: `PlayingIndicator` in `primary` while this row is `playingId`; otherwise `ic_bell_off` (Silent) or `ic_bell_ringing` (every other Ringtone, Default included) tinted `onSurface` |
| 6 dp gap | |
| Name | `titleSmall` `onSurface`, single line, ellipsised, `weight(1f)`; the text is `ringtone.name` resolved as below |
| Trailing | only when `selected`: an 18 dp `primary` circle with `ic_check` at 14.4 dp in `onPrimary`; nothing otherwise, the name takes the space |

Nothing else on the row changes while a Preview plays: the row keeps its height and geometry, the check stays where it is, and selection and playback are two independent states (a row can be selected and silent, or playing while another row is selected only for the instant between the tap and the State update).

### `PlayingIndicator` (feature-owned)

Three vertical bars in an 18 dp box drawn with `Canvas`, `primary`, bar width 3 dp with 3 dp gaps, each bar's height animated between 30 % and 100 % of the box by one `rememberInfiniteTransition` with three phase-shifted tweens of about 600 ms (`RepeatMode.Reverse`). It lives next to `RingtoneRow` in `:feature:alarms:presentation`: one use, so not a design-system component. Its `contentDescription` is the screen's `playing` string. No reduced-motion branch: Compose has no common API for it and the motion is small and local.

### Ringtone names

`Ringtone.name` is a `RingtoneName` (ADR-0008, amended by this spec), never a display string. `RingtoneNameText.kt` in `:feature:alarms:presentation` maps it once for both screens:

| `RingtoneName` | String resource | English |
| --- | --- | --- |
| `Silent` | `ringtone_ui_silent` | "Silent" |
| `Default(platformTitle = null)` | `ringtone_ui_default` | "Default" |
| `Default(platformTitle = "Bright Morning")` | `ringtone_ui_defaultWithName` (one argument) | "Default (Bright Morning)" |
| `Named("Cuckoo Clock")` | none, the text as is | "Cuckoo Clock" |

`Named` texts are proper names (Android media-provider titles, the three bundled iOS files) and are not translated. The resources live in this module because Alarm Settings renders the same name on its ringtone row; no data module touches string resources.

### Loading

The top bar renders at once; the list appears when `GetRingtones` returns (a `RingtoneManager` cursor read, a few milliseconds; the iOS list is static). No spinner, no placeholder rows, same stance as Alarm Settings.

## Behaviour

- **Route argument**: `savedStateHandle.toRoute<RootRoute.RingtoneSetting>().ringtoneId?.toRingtoneId() ?: RingtoneId.Default` read in the ViewModel. `null` is allowed by the route type but never sent by Alarm Settings.
- **Preselection**: after `GetRingtones` returns, the selected row is the one whose id equals the incoming id; when no row matches (a `Platform` id that no longer resolves, i.e. a deleted custom tone) the **Default** row is shown selected, mirroring the `find(id) ?: default` rule Alarm Settings renders with — and **nothing is written** to the result: the draft keeps its unresolvable id until the user taps (ADR-0008: no rewriting behind the user's back). `Silent` and `Default` always match.
- **Tap** (`OnRingtoneClick(id)`), in this order: `selectedId = id` in State; `SELECTED_ID = id.encode()` in the handle; the `SelectionChanged(id.encode())` event, which the Root turns into the result write; then the Preview below. Every tap writes, including a tap on the already selected row (the write is idempotent: Alarm Settings updates its draft to the same value and re-resolves the same name) and including the very first tap. The result is therefore in the previous entry's handle from the first tap on, whatever way the user leaves afterwards — no work on pop, no back interception, no window in which a process death loses a choice.
- **Result write**: `RingtoneSettingRoot(onRingtoneSelected: (String) -> Unit)`; `alarmsGraph` implements it as `navController.previousBackStackEntry?.savedStateHandle?.set(RingtoneSettingResult.KEY, encodedId)`. Not a navigation call, so no `dropUnlessResumed`. After process death the previous entry's handle still holds the last write (it is saved state), so restoration re-publishes nothing.
- **Preview**: a tap on any row other than Silent cancels the running `previewJob` and starts a new one: `playingId = id` → `PlayRingtonePreview(id)` (suspends until the Preview ends — natural end, the 30 s cap, or cancellation, which stops the player; ADR-0008 amended) → `playingId = null`. Last tap wins; there is no queue. A tap on the selected row plays it again from the start. A tap on **Silent** cancels the job and calls `StopRingtonePreview` (`playingId = null`). `Default` on iOS: `play` returns at once because there is nothing to play (ADR-0008), so the indicator never shows — no platform check in the ViewModel or the Screen. No text, hint or snackbar explains a silent Default or Silent row.
- **Preview failure**: `PlayRingtonePreview` returns `Result.failure` (an unreadable `content://` URI, denied audio focus); the use case reports it once through `ErrorReporter`, `playingId` goes back to `null`, the selection and the result write stand, and nothing is shown. Hearing the tone matters less than choosing it, and a snackbar over a list being scrolled is noise.
- **Stop on leaving and on background**: the Root declares `LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { onAction(OnScreenPaused) }`; the ViewModel cancels `previewJob` (which stops the player) and calls `StopRingtonePreview` as a safety net, `playingId = null`. `onCleared()` cancels `viewModelScope`, so popping the screen stops the Preview with no explicit action; back is a plain `NavigateBack`. On iOS the `NavHost` drives the same lifecycle, so the back swipe and backgrounding behave alike. Returning to the foreground does not resume the Preview.
- **Back**: `OnBackClick` → `NavigateBack`; the system back and the iOS back swipe go through the `NavHost` unchanged. There is no confirmation and nothing to confirm: the draft already holds the last tap.
- **Process death**: `SELECTED_ID` (encoded) is written on every tap and read in `init` before the route argument; when present it wins, because the draft in Alarm Settings already has that value and a check on the old row would contradict it. The list is re-read (`GetRingtones` is cheap and the Android catalog may have changed). `playingId` is not saved: a Preview does not survive process death. The key is a public constant in the companion so tests build the handle from a map (`kmp-testing`).
- **Failures**: `GetRingtones` failing → `ShowOperationFailed` ("Something went wrong" snackbar, no action), the list stays empty, back works. The Android catalog itself never fails on the cursor (ADR-0008 amended: `all()` wraps the `RingtoneManager` read and falls back to Silent + Default), so the empty-list case is a real defect, not a device state.

## Domain and contract changes

```kotlin
// :core:ringtone:domain — amends ADR-0008
sealed interface RingtoneName {
    data object Silent : RingtoneName
    /** Android: the title of the tone the system default currently resolves to (null when it cannot be read). iOS: null. */
    data class Default(val platformTitle: String?) : RingtoneName
    /** A media-provider title on Android; a bundled file's display name on iOS. Shown as is, never translated. */
    data class Named(val text: String) : RingtoneName
}

data class Ringtone(val id: RingtoneId, val name: RingtoneName)   // was displayName: String

interface RingtonePreviewPlayer {
    /** Plays the Ringtone once at full player gain and suspends until the Preview ends: the file's end, the 30-second cap, or stop(). Cancelling the call stops the Preview. A second call stops the previous Preview first. Silent only stops and returns at once; so does Default on iOS. */
    suspend fun play(id: RingtoneId)
    fun stop()
}
```

Android implements "suspends until the end" by polling `Ringtone.isPlaying()` every 250 ms under the existing 30-second cap (`Ringtone` has no completion callback); iOS resumes the continuation from `audioPlayerDidFinishPlaying` or the cap, whichever comes first. Both stop the player in a `finally` so cancellation is a stop.

Use cases in `:feature:alarms:domain` (`UseCase` + `Result`, `kmp-error-handling`; amends ADR-0004's use-case list):

| Use case | Does |
| --- | --- |
| `GetRingtones(): Result<List<Ringtone>>` | `catalog.all()`, one-shot, catalog order (Silent, Default, platform entries) |
| `PlayRingtonePreview(id): Result<Unit>` | `player.play(id)`; returns when the Preview ends; failure reported once |
| `StopRingtonePreview(): Result<Unit>` | `player.stop()` |

The ViewModel takes use cases only: `RingtoneSettingViewModel(getRingtones: GetRingtones, playRingtonePreview: PlayRingtonePreview, stopRingtonePreview: StopRingtonePreview, savedStateHandle: SavedStateHandle)`. The catalog and the player are operations, not inputs to a pure mapping, so the `clock`/`capabilities` deviation the other specs record does not apply here.

## MVI contract

```kotlin
data class RingtoneSettingState(
    val ringtones: List<Ringtone> = emptyList(),   // empty until GetRingtones returns
    val selectedId: RingtoneId,                    // route argument, normalised to Default when unresolvable
    val playingId: RingtoneId? = null,             // non-null → PlayingIndicator on that row
)

sealed interface RingtoneSettingAction {
    data class OnRingtoneClick(val id: RingtoneId) : RingtoneSettingAction
    data object OnBackClick : RingtoneSettingAction
    data object OnScreenPaused : RingtoneSettingAction
}

sealed interface RingtoneSettingEvent {
    data class SelectionChanged(val ringtoneId: String) : RingtoneSettingEvent   // encoded; Root writes the result
    data object NavigateBack : RingtoneSettingEvent
    data object ShowOperationFailed : RingtoneSettingEvent
}
```

State holds domain values (`Ringtone`, `RingtoneId`, `RingtoneName`) and no strings: the names, the icons and the content descriptions are resolved in the Screen. The screen's own resources are `ringtoneSetting_ui_back`, `ringtoneSetting_ui_playing` and `ringtoneSetting_ui_operationFailed`; the name resources are the shared `ringtone_ui_*` above. `onAction` is exhaustive. `RingtoneSettingRoot` maps `SelectionChanged` to `onRingtoneSelected`, `NavigateBack` to the nav callback (`dropUnlessResumed`), `ShowOperationFailed` to `SnackbarHostState.showSnackbar`, and owns the `LifecycleEventEffect` that sends `OnScreenPaused`.

## Acceptance criteria

1. Opening from Alarm Settings shows the back button and, in catalog order, Silent, Default and the platform Ringtones — the device `TYPE_ALARM` tones on Android, Bright Morning / Cuckoo Clock / Early Twilight on iOS — with the draft's Ringtone checked; no title, no spinner.
2. Silent shows the crossed bell, every other row the ringing bell; names read "Silent", "Default (<system default's title>)" on Android, "Default" on iOS, and the tone's own title otherwise, single line, ellipsised.
3. When the draft's Ringtone no longer exists in the catalog, Default is checked and Alarm Settings' draft is unchanged until the user taps a row.
4. Tapping a row checks it, unchecks the previous one, and does not leave the screen.
5. Tapping a non-Silent row plays its Preview once at full gain and shows the animated bars in that row's icon circle for as long as it plays; the bars disappear when the file ends, at 30 s, or when another row is tapped; on iOS tapping Default shows no bars and plays nothing.
6. Tapping the already checked row plays its Preview again from the start.
7. Tapping Silent checks it and stops any Preview; tapping a second row while a Preview plays stops the first and starts the second, with only one Preview audible at any time.
8. Leaving the screen by the back button, the system back or the iOS back swipe, and sending the app to the background, stops the Preview; coming back does not resume it.
9. After any tap, Alarm Settings' ringtone row shows the tapped Ringtone's name on return, whichever way the user leaves; leaving without tapping leaves the draft as it was; a second visit preselects the last tapped Ringtone.
10. After process death mid-visit the last tapped row comes back checked (or the route's Ringtone when nothing was tapped), the list is re-read, and no Preview plays.
11. A Preview that cannot start leaves the row checked and the result written, with no message; if the catalog cannot be read a "Something went wrong" snackbar appears over an empty list and back still works.
12. Rows are exposed to accessibility as a radio group with the checked row selected; the animated bars carry the "Playing" description.
13. `RingtoneSettingViewModel` tests cover each Action → State/Event route with mocked use cases: preselection from the route and from `SELECTED_ID`, normalisation of an unresolvable id, `SelectionChanged` on every tap, `playingId` set while a suspending mock `PlayRingtonePreview` runs and cleared when it returns or fails, last-tap-wins cancellation, Silent stopping, `OnScreenPaused` stopping, and `ShowOperationFailed` on a failing `GetRingtones`; `RingtoneName` → string mapping and `encode`/`toRingtoneId` have `commonTest` coverage.
