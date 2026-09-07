# Screen spec: Alarm Settings, time input and name dialog

Decided in [Screen spec: Alarm Settings, time input and name dialog (#17)](https://github.com/IgorJonski/Snoozeloo/issues/17) on 2026-09-07. Inputs: the Figma settings frame (`docs/design/screens/alarm-settings.png`, tokens in `docs/design/README.md`), the PDF "Alarm Detail Screen" bullet list, ADR-0004 (route `RootRoute.AlarmSettings(alarmId?)`, the name dialog as screen state, the Ringtone Setting result through `previousBackStackEntry.savedStateHandle`), ADR-0005 (`nextOccurrence`, rule 6 on what drops a Snoozed Occurrence), ADR-0007 (`AlarmCapabilities` hides Volume and Vibrate on iOS; the alert title is the Name or the Alarm Time), ADR-0008 (`RingtoneId.encode()`, the row renders `find(id) ?: default`), ADR-0009 (`TimeDigitField`, `SettingRow`, `DayChip`, `VolumeSlider`, `SnoozelooSwitch(Row)`, `SnoozelooDialog`, `SquareIconButton`, `PrimaryButton`), ADR-0010 (`upsert`, `createdAt`, the save use case compares against the stored Alarm). Conventions: `kmp-presentation-mvi` (State/Action/Event, Root/Screen split, `SavedStateHandle` for typed input and standalone chrome), `kmp-navigation` (route arguments read in the ViewModel) and `kmp-compose-ui`. Glossary terms are `CONTEXT.md`'s. The settled-while-charting rules apply: the time editor is always 24 h with 00:00–23:59 validation, and Delete is available here for existing Alarms.

The screen lives in `:feature:alarms:presentation` as `AlarmSettingsScreen.kt` (`AlarmSettingsRoot` above `AlarmSettingsScreen`), driven by `AlarmSettingsViewModel`. It is reached from the Alarm List with `alarmId = null` (new Alarm) or an id (edit), and it edits a **draft**: nothing is written until Save.

## Layout

`Scaffold` on `background`. The top bar is fixed: `SquareIconButton(ic_cross)` (close) at the leading edge and `PrimaryButton("Save", enabled = state.canSave)` at the trailing edge, both 16 dp below the status bar, 16 dp horizontal padding. The content below is a `Column` with `verticalScroll` and `imePadding`, 16 dp horizontal padding, starting 24 dp under the top bar (Figma: bar at y = 68, first card at y = 124), 16 dp between cards, and a bottom padding of 32 dp so the last element clears the home indicator. The numeric keyboard therefore pushes the cards up instead of covering them.

| Card / row | Content | Shown when |
| --- | --- | --- |
| Time | `SnoozelooCard(contentPadding = 24.dp)`: a centred `Row` of `TimeDigitField` (hour), a `displayMedium` `onSurfaceVariant` colon with 10 dp on each side, `TimeDigitField` (minute); 16 dp below, centred, `bodyMedium` `onSurfaceVariant` "Alarm in 7h 15min" | always; the countdown line only while the time is valid (the card shrinks otherwise) |
| Alarm Name | `SettingRow("Alarm Name", onClick, trailing = name)` — the trailing value is `bodyMedium` `onSurfaceVariant`, single line, ellipsised; empty when there is no Name | always |
| Repeat | `SnoozelooCard`: label "Repeat" `titleMedium` `onSurface`, 10 dp gap, `RepeatDaysRow(selected, onToggle)` — seven `DayChip`s Mo … Su `space-between`, clickable | always |
| Alarm ringtone | `SettingRow("Alarm ringtone", onClick, trailing = ringtoneName)` — the `RingtoneName` resolved through the shared `RingtoneNameText.kt` mapping (`docs/specs/ringtone-setting.md`); empty while unresolved | always |
| Alarm volume | `SnoozelooCard`: label "Alarm volume" `titleMedium`, 10 dp gap, `VolumeSlider(value, onValueChange)` | `AlarmCapabilities.hasVolume` |
| Vibrate | `SettingRow("Vibrate", trailing = SnoozelooSwitch(SwitchSize.Row))` | `AlarmCapabilities.hasVibrate` |
| Delete | Material `TextButton` "Delete alarm", `titleMedium`, `error`, centred, 16 dp below the last card | editing an existing Alarm |

`RepeatDaysRow` is the feature-owned composable the Alarm List also uses (display-only there, `onToggle = null`); the seven labels are its string resources (`repeatDays_ui_mo` … `repeatDays_ui_su`). On iOS the Volume card and the Vibrate row are absent — not disabled, not greyed — and the Delete button follows the Ringtone row. The ViewModel copies the two flags from `AlarmCapabilities` into State so the Screen has no platform check.

### Alarm Name dialog

`SnoozelooDialog` drawn inside the screen: label "Alarm Name" `titleMedium` `onSurface`, 10 dp gap, `SnoozelooTextField` (single line, `KeyboardCapitalization.Sentences`, `ImeAction.Done`), 10 dp gap, `PrimaryButton("Save")` right-aligned. On open the field gets focus through a `FocusRequester` and the keyboard shows; `Done` acts as Save. There is no Cancel button (Figma has none): a tap on the scrim or the system back closes the dialog and discards the typed text.

### Delete confirmation dialog

The same `SnoozelooDialog`: "Delete this alarm?" `titleMedium` `onSurface`, 16 dp gap, then a right-aligned row of `SecondaryButton("Cancel")` and `PrimaryButton("Delete")` 10 dp apart, both with the small (16 h / 6 v) padding. The `error` colour stays on the button that opened the dialog; the dialog uses the stock components. Scrim tap and system back cancel.

### Loading

For an existing Alarm the screen renders the top bar with Save disabled and no cards until `GetAlarm` returns (a few milliseconds; no spinner). A new Alarm renders immediately with the defaults.

## Time input rules (pure functions, `commonTest`)

The two fields hold raw text (`hourText`, `minuteText`), zero to two characters each; every rule below lives in the ViewModel (or a pure helper it calls), never in `TimeDigitField`.

- **Accept or reject**: `acceptTimeInput(current: String, proposed: String, max: Int): String` keeps only digits, truncates to two characters, and returns `current` unchanged when the result parses above `max` (23 for the hour, 59 for the minute). Deleting is always accepted. So `"2"` + `"4"` stays `"2"`, `"0"` + `"7"` becomes `"07"`, `"7"` is a valid hour on its own.
- **Valid time**: both texts non-empty. `AlarmTime(hourText.toInt(), minuteText.toInt())`. The fields can never hold an out-of-range value, so validity has no other case, and there is no error state or error colour (Figma has none).
- **Padding**: when a field loses focus with one character, it becomes `"0" + char`; an empty field stays empty. Save also pads through `toInt()`, so an unpadded field is never wrong, only unpadded.
- **Auto-advance**: when an accepted hour edit takes the text from fewer than two to two characters, the ViewModel sends `MoveFocusToMinute`; the Screen holds the minute `FocusRequester`. No advance after one character (`"7"` may become `"17"`), no advance out of the minute field, no automatic keyboard on entering the screen (Figma shows the placeholder `00` with the keyboard closed). The hour field uses `ImeAction.Next`, the minute field `ImeAction.Done` (clears focus).
- **Prefill**: an existing Alarm shows its time as `%02d` in each field, digits in `primary`.
- **Countdown**: `formatCountdown(nextRegularOccurrence(time, repeatDays, now, zone), now)` — the same `formatCountdown` as the Alarm List, on a new pure function `nextRegularOccurrence(time: AlarmTime, repeatDays: RepeatDays, now: Instant, zone: TimeZone): Instant` in `:core:alarm-scheduling:domain` that `nextOccurrence(alarm, now, zone)` now delegates to (amends ADR-0005; same rules 2–4). It is computed from the **draft**, never from the stored Alarm, and never from `snoozedUntil`: the line answers "when does this time with these days ring", and a pending Snoozed Occurrence is dropped anyway the moment Alarm Time or Repeat Days change. It re-derives on every accepted keystroke, on every chip toggle and on a minute ticker identical to the list's.

## Behaviour

- **Route argument**: `savedStateHandle.toRoute<RootRoute.AlarmSettings>().alarmId` read in the ViewModel (`kmp-navigation`); `null` means a new Alarm. The same `SavedStateHandle` carries the draft (process death) and the Ringtone Setting result — one object, three roles.
- **Defaults for a new Alarm**: empty time (placeholder `00` : `00`, Save disabled), no Name, no Repeat Days (One-shot), `RingtoneId.Default`, Volume 50, Vibrate `true` (the Figma toggle is on). On iOS the hidden Volume and Vibrate still save as 50 / `true` — the columns are `NOT NULL` and the values do nothing there.
- **Load for an existing Alarm**: `GetAlarm(id)` once, in `init`, unless the handle already holds a draft (restoration). The editor works on a copy: a stored change made meanwhile by the ringing path (`setSnoozedUntil`, a One-shot's `setEnabled(false)`) does not rewrite the fields under the user's fingers, and the save use case reads the fresh row at save time. A missing Alarm or a failed read sends `NavigateBack` with no message: it cannot happen from the app's own flows (the list, the only place that deletes, is underneath this screen).
- **Save enablement**: `canSave = time valid && !isSaving && !isLoading`. No dirty tracking: Figma has none, and with the rule below "Save without changes" is meaningful (it enables the Alarm).
- **Save**: `OnSaveClick` → `isSaving = true` (disables the button against a double tap) → the permission hook that [Permission and authorization UX on both platforms (#22)](https://github.com/IgorJonski/Snoozeloo/issues/22) defines, exactly as the list's toggle → `CreateAlarm(draft)` or `UpdateAlarm(id, draft)` → `NavigateBack`. Failure: `isSaving = false` and `ShowOperationFailed` ("Something went wrong" snackbar, no action). No toast on success: the list's countdown is the feedback. **A saved Alarm is always Enabled**, new or edited, even if it was Disabled before: the user just configured it and expects it to ring (Android Clock does the same). A One-shot whose time already passed today is scheduled for tomorrow (ADR-0005 rule 2). A change to Alarm Time or Repeat Days drops the pending Snoozed Occurrence; a change to Name, Ringtone, Volume or Vibrate keeps it (ADR-0005 rule 6, enforced in `UpdateAlarm`).
- **Name dialog**: `OnNameClick` opens it with the current Name as the text. `OnNameDialogSave` commits `text.trim().ifBlank { null }` to the draft — an empty field removes the Name, so the dialog's Save is always enabled. `OnNameDialogDismiss` (scrim, back) closes it and discards the text. No character cap: single line, ellipsised where displayed (the card, the AlarmKit alert title). Nothing is persisted until the screen's Save.
- **Repeat Days**: `OnRepeatDayToggle(day)` flips the day in the draft; no select-all, no minimum.
- **Ringtone round trip**: `OnRingtoneClick` → `NavigateToRingtoneSetting(draft.ringtoneId.encode())`; the Root navigates to `RootRoute.RingtoneSetting(ringtoneId)` guarded with `dropUnlessResumed`. Ringtone Setting writes the chosen id, encoded, to `previousBackStackEntry.savedStateHandle[RingtoneSettingResult.KEY]` and pops (its own behaviour is [Screen spec: Ringtone Setting (#18)](https://github.com/IgorJonski/Snoozeloo/issues/18); this key is the whole contract). `AlarmSettingsViewModel` collects `savedStateHandle.getStateFlow<String?>(RingtoneSettingResult.KEY, null)` from `init`; a non-null value updates the draft, then the key is set back to `null` so a second visit starts clean. `RingtoneSettingResult { const val KEY = "selectedRingtoneId" }` lives in `:core:navigation:domain` next to the routes (amends ADR-0004) because two screens share it.
- **Ringtone name**: `GetRingtone(id)` (`catalog.find(id) ?: catalog.find(Default)`) on load and after every change of the draft's id; the result's `name` (a `RingtoneName`, ADR-0008 as amended by `docs/specs/ringtone-setting.md`) fills the row; the Screen maps it to the `ringtone_ui_*` resources. On failure the row value is empty and the row stays tappable — no name we have not confirmed is shown.
- **Volume**: `OnVolumeChange(percent)` on every slider movement (`onValueChange`), integer 0–100, no steps, no haptics. **Vibrate**: `OnVibrateChange(vibrate)` from the switch; no optimistic concerns, it is draft state.
- **Delete**: `OnDeleteClick` opens the confirmation; `OnDeleteConfirm` runs `DeleteAlarm(id)` (scheduler cancel + hard delete, ADR-0010) and sends `NavigateBack`; failure closes the dialog and sends `ShowOperationFailed`. No undo from here: the list has its own undo for swipe, and an undo after popping would need the deleted Alarm carried back through the back stack. `OnDeleteDialogDismiss` closes the dialog.
- **Close and back**: `OnCloseClick` → `NavigateBack`, no "discard changes?" dialog even with edits — Figma has none, the screen is short, and there is no dirty tracking to base it on. The system back does the same through the `NavHost` when no dialog is open. When a dialog is open, back closes the dialog: the Screen declares `NavigationBackHandler(currentInfo = NavigationEventInfo.None, isBackEnabled = state.nameDialog != null || state.isDeleteDialogOpen, onBackCompleted = { onAction(dismiss of the open dialog) })` from `org.jetbrains.androidx.navigationevent:navigationevent-compose` (the version CMP 1.12.0 ships). `androidx.compose.ui.backhandler.BackHandler` and `PredictiveBackHandler` are deprecated in favour of the navigation-event handlers and are not used. On iOS the `NavHost` back swipe is the same event, so the dialog closes there too.
- **Process death**: every draft field is written to `SavedStateHandle` on the action that changes it and read back in `init` — `HOUR_TEXT`, `MINUTE_TEXT`, `NAME`, `REPEAT_DAYS` (bitmask `Int`, ADR-0010 encoding), `RINGTONE_ID` (encoded `String`), `VOLUME`, `VIBRATE`, plus the standalone chrome: `NAME_DIALOG_TEXT` (`null` when the dialog is closed) and `DELETE_DIALOG_OPEN`. When `HOUR_TEXT` is present the draft is restored and `GetAlarm` is skipped: the draft wins over the stored row. Keys are public constants in the companion so tests build the handle from a map (`kmp-testing`). `isSaving` is not saved; a save interrupted by process death is simply not done.
- **Failures**: `CreateAlarm`, `UpdateAlarm`, `DeleteAlarm` failing → `ShowOperationFailed`, stay on the screen; `GetAlarm` failing or `null` → `NavigateBack`; `GetRingtone` failing → empty row value. Room failing locally is not worth separate copy (same stance as the list).

## Domain additions

```kotlin
// :core:alarm:domain — what the screen edits; the domain fills the rest of Alarm
data class AlarmDraft(
    val name: String?,
    val time: AlarmTime,
    val repeatDays: RepeatDays,
    val ringtoneId: RingtoneId,
    val volume: Volume,
    val vibrate: Boolean,
)

// :core:navigation:domain
object RingtoneSettingResult { const val KEY = "selectedRingtoneId" }

// :core:alarm-scheduling:domain — extracted from nextOccurrence
fun nextRegularOccurrence(time: AlarmTime, repeatDays: RepeatDays, now: Instant, zone: TimeZone): Instant
```

Use cases in `:feature:alarms:domain` (`UseCase` + `Result`, `kmp-error-handling`); `CreateAlarm` and `UpdateAlarm` replace the `SaveAlarm` named in ADR-0004 and ADR-0010 — two use cases instead of one with a nullable id, each a single path:

| Use case | Does |
| --- | --- |
| `GetAlarm(id): Result<Alarm?>` | `repository.get(id)`, one-shot |
| `GetRingtone(id): Result<Ringtone>` | `catalog.find(id) ?: catalog.find(RingtoneId.Default)!!` |
| `CreateAlarm(draft): Result<Unit>` | `Alarm(id = Uuid.random(), …draft, enabled = true, snoozedUntil = null, createdAt = clock.now())` → `repository.upsert` → `scheduler.sync` |
| `UpdateAlarm(id, draft): Result<Unit>` | reads the stored Alarm; `createdAt` carried over; `snoozedUntil = if (draft.time != stored.time \|\| draft.repeatDays != stored.repeatDays) null else stored.snoozedUntil`; `enabled = true` → `upsert` → `sync`. A stored row that no longer exists is created as `CreateAlarm` would, keeping the id |

The read-then-upsert in `UpdateAlarm` is not transactional: a Snooze recorded between the two statements is lost. The window is a few milliseconds inside one save; accepted.

## MVI contract

```kotlin
data class AlarmSettingsState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val hourText: String = "",
    val minuteText: String = "",
    val countdown: String? = null,           // "7h 15min"; null → line omitted
    val name: String? = null,
    val repeatDays: RepeatDays = RepeatDays.NONE,
    val ringtoneName: RingtoneName? = null,  // null → empty row value; the Screen resolves the string
    val volume: Int = 50,
    val vibrate: Boolean = true,
    val hasVolume: Boolean,                  // AlarmCapabilities → card shown
    val hasVibrate: Boolean,                 // AlarmCapabilities → row shown
    val isSaving: Boolean = false,
    val nameDialog: NameDialogState? = null, // non-null → dialog open
    val isDeleteDialogOpen: Boolean = false,
) {
    val canSave: Boolean get() = hourText.isNotEmpty() && minuteText.isNotEmpty() && !isSaving && !isLoading
}

data class NameDialogState(val text: String)

enum class TimeField { Hour, Minute }

sealed interface AlarmSettingsAction {
    data class OnHourChange(val text: String) : AlarmSettingsAction
    data class OnMinuteChange(val text: String) : AlarmSettingsAction
    data class OnTimeFieldFocusLost(val field: TimeField) : AlarmSettingsAction
    data object OnNameClick : AlarmSettingsAction
    data class OnNameDialogTextChange(val text: String) : AlarmSettingsAction
    data object OnNameDialogSave : AlarmSettingsAction
    data object OnNameDialogDismiss : AlarmSettingsAction
    data class OnRepeatDayToggle(val day: DayOfWeek) : AlarmSettingsAction
    data object OnRingtoneClick : AlarmSettingsAction
    data class OnVolumeChange(val percent: Int) : AlarmSettingsAction
    data class OnVibrateChange(val vibrate: Boolean) : AlarmSettingsAction
    data object OnSaveClick : AlarmSettingsAction
    data object OnCloseClick : AlarmSettingsAction
    data object OnDeleteClick : AlarmSettingsAction
    data object OnDeleteConfirm : AlarmSettingsAction
    data object OnDeleteDialogDismiss : AlarmSettingsAction
}

sealed interface AlarmSettingsEvent {
    data object NavigateBack : AlarmSettingsEvent
    data class NavigateToRingtoneSetting(val ringtoneId: String) : AlarmSettingsEvent   // encoded
    data object MoveFocusToMinute : AlarmSettingsEvent
    data object ShowOperationFailed : AlarmSettingsEvent
}
```

State holds only what the Screen draws; "Alarm in", the row labels, button texts and content descriptions are string resources resolved in the Screen (`alarmSettingsScreen_ui_*`: `save`, `close`, `alarmName`, `repeat`, `alarmRingtone`, `alarmVolume`, `vibrate`, `countdown` with one argument, `hourField`, `minuteField`, `nameDialogSave`, `deleteAlarm`, `deleteConfirmation`, `cancel`, `delete`, `operationFailed`; the day labels are `repeatDays_ui_*`).

`AlarmSettingsViewModel(getAlarm: GetAlarm, getRingtone: GetRingtone, createAlarm: CreateAlarm, updateAlarm: UpdateAlarm, deleteAlarm: DeleteAlarm, capabilities: AlarmCapabilities, clock: Clock, timeZoneProvider: TimeZoneProvider, savedStateHandle: SavedStateHandle)`. `clock`, `timeZoneProvider` and `capabilities` are the same deliberate deviation from the use-cases-only rule the Alarm List spec records: inputs to a pure presentation mapping, not operations; `capabilities` is a constructor parameter rather than a direct object read so tests can flip it. `onAction` is exhaustive. `AlarmSettingsRoot` maps `NavigateBack` and `NavigateToRingtoneSetting` to the nav callbacks (`dropUnlessResumed`), `MoveFocusToMinute` to the minute `FocusRequester`, `ShowOperationFailed` to `SnackbarHostState.showSnackbar`.

## Acceptance criteria

1. Opening from the FAB shows empty fields with the `00` placeholders, Save disabled, no Name, no selected chips, "Default" as the ringtone, Volume 50, Vibrate on, and no Delete button; the keyboard is closed.
2. Opening from a card shows the Alarm's time as two padded 24 h digits in `primary`, its Name, chips, ringtone name, Volume and Vibrate, Save enabled, and the Delete button; until the Alarm is read, only the top bar shows with Save disabled.
3. Each time field accepts digits only, at most two; a keystroke that would make the hour exceed 23 or the minute exceed 59 is ignored and the field keeps its previous text; deletion always works.
4. Save is enabled exactly when both fields are non-empty (a single digit counts), and disabled while a save is in flight.
5. Typing the second hour digit moves focus to the minute field; typing one digit does not; `Done` on the minute field closes the keyboard.
6. A single-digit field is padded with a leading zero when it loses focus; an empty field stays empty.
7. While the time is valid, "Alarm in …" appears under the fields, computed from the draft time and chips (not the stored Alarm, not a pending Snoozed Occurrence), formatted like the list (`30min`, `5h 30min`, `1d 45min`), updating on every accepted keystroke, chip toggle and whole minute; it is absent while the time is invalid.
8. On Android the Volume card and the Vibrate row are shown; on iOS neither is, and the layout has no gap where they would be.
9. Tapping the Alarm Name row opens the dialog with the current Name, focus in the field and the keyboard shown; Save (button or `Done`) commits the trimmed text and a blank text removes the Name; scrim tap or back closes the dialog without changing the Name; the row shows the Name or nothing.
10. Tapping a chip toggles that day; any combination including none is allowed.
11. Tapping the ringtone row opens Ringtone Setting with the draft's ringtone preselected; returning with a different choice updates the row's name, and returning without a choice leaves it; the second visit preselects the new choice.
12. Save creates or updates the Alarm with the draft's fields, sets it Enabled (also when it was Disabled), schedules it, and returns to the list with no toast; the new or edited card appears in stored order.
13. Editing only Name, Ringtone, Volume or Vibrate keeps a pending Snoozed Occurrence; editing Alarm Time or Repeat Days drops it.
14. Saving an existing Alarm without changes leaves every field as it was and enables it.
15. Close (X) or system back with no dialog open returns to the list and discards every edit without a prompt; with a dialog open, back closes the dialog only.
16. Tapping "Delete alarm" shows "Delete this alarm?" with Cancel and Delete; Delete removes the Alarm, cancels its platform schedule (Snoozed Occurrence included) and returns to the list; Cancel, scrim or back keeps it.
17. After process death mid-edit the fields, Name, chips, ringtone, Volume, Vibrate and an open name or delete dialog (with the typed text) come back exactly as left, without re-reading the stored Alarm.
18. If saving or deleting fails a "Something went wrong" snackbar appears and the screen stays with the draft intact; if the Alarm cannot be read the screen returns to the list; if the ringtone name cannot be resolved the row value is empty and still tappable.
19. `acceptTimeInput`, the padding rule, the auto-advance condition and `nextRegularOccurrence` have `commonTest` coverage (boundaries 23/59, leading zeros, deletion, today/tomorrow, weekday horizon); `AlarmSettingsViewModel` tests cover each Action → State/Event route with mocked use cases, a fake clock, both `AlarmCapabilities` values and a `SavedStateHandle` built from a map (restoration skips `GetAlarm`; the ringtone result key updates the draft and is cleared); `CreateAlarm` and `UpdateAlarm` tests cover `createdAt`, `enabled = true` and the `snoozedUntil` rule.
