# Screen spec: Alarm List and empty state

Decided in [Screen spec: Alarm List and empty state (#16)](https://github.com/IgorJonski/Snoozeloo/issues/16) on 2026-09-07. Inputs: the Figma list frame (`docs/design/screens/alarm-list.png`, tokens in `docs/design/README.md`), the PDF "Alarm List Screen" bullet list, ADR-0004 (route `RootRoute.AlarmList`, use cases in `:feature:alarms:domain`), ADR-0005 (`nextOccurrence`, Snoozed Occurrence as the Next Occurrence), ADR-0009 (`SnoozelooCard`, `SnoozelooSwitch(Card)`, `DayChip`, `Fab`, `error*` slots for swipe-to-delete), ADR-0010 (list order, hard delete + `RestoreAlarm` undo, `createdAt`). Conventions: `kmp-presentation-mvi` (State/Action/Event, Root/Screen split, semantic errors) and `kmp-compose-ui`. Glossary terms are `CONTEXT.md`'s.

The screen lives in `:feature:alarms:presentation` as `AlarmListScreen.kt` (`AlarmListRoot` above `AlarmListScreen`), driven by `AlarmListViewModel`. It is the start destination of `alarmsGraph`.

## Layout

`Scaffold` on `background`, title "Your Alarms" (`headlineSmall`, `onSurface`) 16 dp below the status bar, `Fab` centred at the bottom (`floatingActionButtonPosition = Center`, bottom edge 50 dp above the screen bottom as in Figma), `SnackbarHost` in the Scaffold slot so the undo snackbar sits above the FAB.

Content is a `LazyColumn` with `key = alarm.id`, 16 dp horizontal padding, 16 dp between cards, and a bottom `contentPadding` of about 110 dp so the last card scrolls clear of the FAB (the Figma render shows the FAB over the last card's chips: that is the unscrolled state, not a layout rule).

### Alarm card (`AlarmCard`, feature-owned)

`SnoozelooCard` (white, radius 10, padding 16) with these rows, top to bottom. A row that has nothing to show is **omitted**, not left blank; the card shrinks.

| Row | Content | Shown when |
| --- | --- | --- |
| Name | `titleMedium` `onSurface`, single line, ellipsised; `SnoozelooSwitch(SwitchSize.Card)` right-aligned, vertically centred on the first visible row | Name is not null or blank |
| Time | `displayMedium` `onSurface` `04:00` (or `16:00` in 24 h mode) with a bottom-aligned `headlineSmall` `AM`/`PM` 4 dp baseline-offset beside it, only in 12 h mode; when the Name row is absent the switch sits on this row | always |
| Countdown | `bodyMedium` `onSurfaceVariant` "Alarm in 1d 4h 45min" | Alarm is Enabled |
| Repeat Days | seven `DayChip`s Mo … Su, `space-between`, display-only (not clickable; a tap counts as a tap on the card) | Alarm has at least one Repeat Day (a One-shot Alarm shows no chip row) |
| Bedtime Hint | `bodyMedium` `onSurfaceVariant` "Go to bed at 02:00 AM to get 8h of sleep" | Alarm Time is between 04:00 and 12:00 inclusive, Enabled or not |

Vertical gaps between present rows: Name → Time 10 dp, Time → next row 8 dp when that row is the Countdown and 16 dp otherwise, every later gap 16 dp (Figma: countdown → chips 16, chips → hint 16).

Rationale for the two Figma deviations: the Figma "Dinner" card shows a countdown on a Disabled Alarm, which contradicts the toggle's meaning (nothing is scheduled), so the Countdown row follows Enabled; Figma has no One-shot card, and a row of seven unselected chips reads as a bug rather than as "no repeat", so the chip row follows Repeat Days.

### Empty state (`EmptyState`, feature-owned)

Shown instead of the list when the loaded list is empty. The title "Your Alarms" and the FAB stay. Centred on the remaining screen: `ic_alarm_clock` at 62 dp tinted `primary`, 32 dp gap, then the message in `bodyLarge` `onSurface`, centred, wrapping to two lines: "It's empty! Add the first alarm so you don't miss an important moment!". The copy is the one from the Snoozeloo design; `docs/design/README.md` did not record it and the Figma MCP budget was exhausted, so it is to be checked against the frame once the budget resets, without blocking implementation.

### Loading and error

Until the first emission from `ObserveAlarms`, the screen shows only the title and the FAB — no spinner, no flash of the empty state. If the stream fails, the screen shows a `bodyLarge` `onSurface` message ("Your alarms could not be loaded.") centred where the list would be, with no retry control; the FAB stays usable.

## Formatting rules (pure functions, `commonTest`)

- **Time display** follows the device 12/24 h setting, read through `TimeFormatPreference` (`fun interface TimeFormatPreference { fun is24Hour(): Boolean }`, `expect`/`actual` in `:feature:alarms:presentation`: Android `DateFormat.is24HourFormat(context)`, iOS `NSDateFormatter.dateFormatFromTemplate("j", 0, NSLocale.currentLocale)` containing no `a`). It is re-read on every `OnResume`. 12 h: `hh:mm` with a leading zero (`04:00`) plus `AM`/`PM`; 24 h: `HH:mm`. The Trigger screen (#19) may lift this type to a shared module; nothing here depends on where it lives.
- **Countdown**: `formatCountdown(until: Instant, now: Instant): String`. Whole minutes rounded **up** (`ceil((until − now) / 1 min)`), split into days, hours, minutes; zero units omitted; units joined by single spaces: `30min`, `5h 30min`, `1d 45min`, `2d`. Because a Next Occurrence is strictly after `now` (ADR-0005 rule 2), the result is never empty and never below `1min`. The instant is `nextOccurrence(alarm, now, zone)` for Enabled Alarms, which already prefers a pending Snoozed Occurrence (ADR-0005 rule 5); the card gives no separate "snoozed" cue.
- **Bedtime Hint**: bedtime = Alarm Time − 8 h, wrapping past midnight (`10:00` → `02:00`, `04:00` → `20:00`); rendered with the same time formatter as the card, with a space before `AM`/`PM` (`02:00 AM`), or `02:00` in 24 h mode. Shown only when `04:00 ≤ Alarm Time ≤ 12:00`.

## Behaviour

- **Ordering**: as stored, `ORDER BY hour, minute, created_at` (ADR-0010 confirmed). The list never re-sorts on toggle or on the clock; a restored Alarm returns to its former place because it keeps its `createdAt`.
- **Toggle**: the switch sends `OnEnabledChange(id, enabled)` → `SetAlarmEnabled` (clears a pending Snoozed Occurrence and syncs the scheduler, ADR-0010/0005). No optimistic state: the card updates when Room re-emits; `SnoozelooSwitch` animates the knob from the new `checked`. No toast or hint after enabling; the Countdown row appearing is the feedback. Enabling a One-shot whose Alarm Time already passed today schedules it for tomorrow (ADR-0005). **Permission gate**: enabling is the point where the platform may need notification / full-screen-intent (Android) or AlarmKit (iOS) authorization; [Permission and authorization UX on both platforms (#22)](https://github.com/IgorJonski/Snoozeloo/issues/22) decides whether the gate runs here, in Alarm Settings, or both. This spec reserves the hook: `OnEnabledChange(enabled = true)` passes through it before `SetAlarmEnabled`.
- **Tap to edit**: a tap anywhere on the card except the switch sends `OnAlarmClick(id)`; the ViewModel answers `NavigateToAlarmSettings(alarmId = id)`. No long-press, no context menu.
- **FAB**: `OnAddClick` → `NavigateToAlarmSettings(alarmId = null)`.
- **Swipe to delete**: `SwipeToDismissBox` around each card, `EndToStart` only (`StartToEnd` disabled), default Material positional threshold, no haptics, no confirmation dialog. Background: `errorContainer` with `ic_delete` (Material Symbols `delete`, 24 dp, tinted `onErrorContainer`) aligned to the trailing edge, same shape as the card. On dismiss the item leaves the list immediately and `OnAlarmSwiped(id)` is sent.
- **Undo**: the ViewModel keeps the swiped `Alarm` in a private field, runs `DeleteAlarm` (scheduler cancel + hard delete), and sends `ShowAlarmDeleted`. The Root shows a Material `Snackbar` ("Alarm deleted", action "Undo") with `SnackbarDuration.Short` (4 s). "Undo" sends `OnUndoDelete` → `RestoreAlarm` (upsert of the kept copy without its Snoozed Occurrence, then scheduler sync). A second swipe while a snackbar is showing replaces both the kept copy and the snackbar: the earlier delete is final. When the snackbar times out the copy is simply dropped on the next swipe; nothing else needs to expire it. Process death inside the window loses the Alarm (accepted in ADR-0010). Nothing is written to `SavedStateHandle`: the screen has no typed input and the undo copy is deliberately not restored.
- **Countdown refresh**: the ViewModel runs a minute ticker — `flow { while (true) { emit(clock.now()); delay(millisUntilNextWholeMinute) } }` in `viewModelScope` (ticks in the background too; the cost is negligible) — and re-derives every `AlarmUi` on each tick and on each `ObserveAlarms` emission. A late tick after Doze self-corrects because each tick reads the real clock. `AlarmListRoot` sends `OnResume` from `LifecycleResumeEffect`, which re-reads `TimeFormatPreference` and re-derives immediately, so a 12/24 h change made in system settings and a countdown stale from the background both show on return.
- **Failures**: a failed `ObserveAlarms` emission sets `error = LoadFailed` (the stream ends; the last known list is replaced by the error message). A failed `SetAlarmEnabled`, `DeleteAlarm` or `RestoreAlarm` sends `ShowOperationFailed`, shown as a snackbar ("Something went wrong") with no action; the cause is not distinguished, Room failing locally is not a case worth its own copy.

## MVI contract

```kotlin
data class AlarmListState(
    val alarms: List<AlarmUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: AlarmListError? = null,
)

data class AlarmUi(
    val id: AlarmId,
    val name: String?,            // null → Name row omitted
    val time: String,             // "04:00" or "16:00"
    val meridiem: String?,        // "AM" / "PM" in 12 h mode, null in 24 h mode
    val countdown: String?,       // "1d 4h 45min"; null when Disabled → row omitted
    val repeatDays: RepeatDays,   // empty → chip row omitted
    val bedtimeHint: String?,     // formatted bedtime, e.g. "02:00 AM"; null outside 04:00–12:00
    val enabled: Boolean,
)

enum class AlarmListError { LoadFailed }

sealed interface AlarmListAction {
    data object OnAddClick : AlarmListAction
    data class OnAlarmClick(val id: AlarmId) : AlarmListAction
    data class OnEnabledChange(val id: AlarmId, val enabled: Boolean) : AlarmListAction
    data class OnAlarmSwiped(val id: AlarmId) : AlarmListAction
    data object OnUndoDelete : AlarmListAction
    data object OnResume : AlarmListAction
}

sealed interface AlarmListEvent {
    data class NavigateToAlarmSettings(val alarmId: AlarmId?) : AlarmListEvent
    data object ShowAlarmDeleted : AlarmListEvent
    data object ShowOperationFailed : AlarmListEvent
}
```

`AlarmUi` carries the bedtime only; the surrounding sentence, `AM`/`PM` and "Alarm in" are string resources resolved in the Screen (`alarmListScreen_ui_*`: `title`, `countdown` with one argument, `bedtimeHint` with one argument, `am`, `pm`, `emptyMessage`, `loadFailedMessage`, `alarmDeleted`, `undo`, `operationFailed`, `addAlarm` and `deleteAlarm` content descriptions).

`AlarmListViewModel(observeAlarms: ObserveAlarms, setAlarmEnabled: SetAlarmEnabled, deleteAlarm: DeleteAlarm, restoreAlarm: RestoreAlarm, clock: Clock, timeZoneProvider: TimeZoneProvider, timeFormatPreference: TimeFormatPreference)`. The three non-use-case parameters are a deliberate deviation from the `kmp-presentation-mvi` rule that a ViewModel takes only use cases: `now`, the zone and the 12/24 h flag are inputs to a pure presentation mapping, not operations, and wrapping each in a use case would add three classes with no logic. They are held outside `State` (as private fields: the last `now`, the last raw `List<Alarm>`, the `is24Hour` flag, the undo copy); `State` carries only rendered `AlarmUi`s. The mapping `Alarm.toAlarmUi(now: Instant, zone: TimeZone, is24Hour: Boolean): AlarmUi` is a pure function in the presentation module, unit-tested together with `formatCountdown` and the bedtime rule.

`onAction` is exhaustive: `OnAddClick` and `OnAlarmClick` emit the navigation event; `OnEnabledChange` calls the use case (through the #22 hook when enabling); `OnAlarmSwiped` keeps the copy, deletes, emits `ShowAlarmDeleted`; `OnUndoDelete` restores the kept copy if there is one and clears it; `OnResume` re-reads the time-format preference and re-derives. `AlarmListRoot` maps `NavigateToAlarmSettings` to the nav callback (guarded with `dropUnlessResumed` per `kmp-navigation`) and the two `Show*` events to `SnackbarHostState.showSnackbar`; the Undo action result of `showSnackbar` sends `OnUndoDelete` back through `onAction`.

## Acceptance criteria

1. With no stored Alarms the screen shows the title, the empty-state icon and message, and the FAB; with at least one Alarm it shows the cards and no empty state; before the first emission it shows only the title and the FAB.
2. Cards appear in stored order (Alarm Time ascending, then creation), and the order does not change when an Alarm is toggled or when time passes.
3. A card with a blank or null Name has no Name row and the switch sits on the Time row.
4. In 12 h mode a card shows `hh:mm` with a leading zero plus `AM`/`PM`; in 24 h mode it shows `HH:mm` and no meridiem; changing the system setting and returning to the app updates every card.
5. An Enabled card shows "Alarm in …" computed from `nextOccurrence`, minutes rounded up, zero units omitted (`30min`, `5h 30min`, `1d 45min`, `2d`); a Disabled card has no Countdown row.
6. While a Snoozed Occurrence is pending, the countdown counts down to it with no extra label.
7. The countdown changes on the next whole minute without any user interaction, and is correct immediately after the app returns from the background.
8. An Alarm with Repeat Days shows seven chips with exactly those days selected; a One-shot Alarm shows no chip row; chips are not interactive (tapping one opens Alarm Settings like the rest of the card).
9. The Bedtime Hint appears for Alarm Times from 04:00 to 12:00 inclusive, Enabled or not, shows Alarm Time minus eight hours in the card's time format (`02:00 AM` / `02:00`), and is absent otherwise.
10. Tapping the switch toggles Enabled through `SetAlarmEnabled`; the card reflects the stored value (no optimistic flip); no toast is shown. Enabling passes through the permission hook that #22 defines.
11. Tapping a card anywhere but the switch opens Alarm Settings for that Alarm; tapping the FAB opens Alarm Settings for a new Alarm; both go through the ViewModel as events.
12. Swiping a card end-to-start past the threshold removes it and shows the "Alarm deleted" snackbar with "Undo" for about 4 s; swiping start-to-end does nothing; the swipe background is `errorContainer` with the delete icon at the trailing edge.
13. Tapping "Undo" restores the Alarm with its previous settings, without a Snoozed Occurrence, at its previous position, and re-schedules it; letting the snackbar expire leaves it deleted.
14. Swiping a second card while the snackbar is visible makes the first deletion final and shows a fresh snackbar for the second.
15. Deleting cancels the platform schedule so the Alarm never rings after the swipe, including a pending Snoozed Occurrence.
16. If loading fails the screen shows the load-failed message and the FAB; if toggle, delete or restore fails a "Something went wrong" snackbar appears and the list shows the stored state.
17. The last card can be scrolled fully above the FAB.
18. `formatCountdown`, the bedtime rule and `Alarm.toAlarmUi` have `commonTest` coverage for the boundary cases above (rounding up, zero units, 04:00 and 12:00 edges, midnight wrap, 12/24 h); `AlarmListViewModel` tests cover each Action → State/Event route with a fake clock and mocked use cases.
