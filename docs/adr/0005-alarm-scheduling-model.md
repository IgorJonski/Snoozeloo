---
status: accepted
---

# Alarm scheduling model: a schedule-level `AlarmScheduler`, one Occurrence per kind, idempotent sync

Android and iOS deliver alarms differently: `AlarmManager.setAlarmClock` takes one instant and the app must schedule the next ring itself, while AlarmKit takes the whole schedule (`.relative(time, .weekly(days))`), repeats it and runs the 5-minute snooze countdown on its own. We therefore put the contract at the level of the **Alarm**, not the Occurrence: `AlarmScheduler.sync(alarm)` makes the platform reflect the Alarm exactly, and each implementation decides how. Next-Occurrence computation is a pure function in `:core:alarm-scheduling:domain` that Android uses for delivery and both platforms use for the list countdown. A **Snoozed Occurrence** is state on the Alarm (`snoozedUntil`), not a separate entity. Drift between the database and the platform (reboot, force-stop, time-zone change, hardware-button stop on iOS) is repaired by one idempotent `syncAll()` run on every app start and on platform hints.

Decided in [Alarm scheduling model: Occurrence computation, Snooze, and the AlarmScheduler contract (#10)](https://github.com/IgorJonski/Snoozeloo/issues/10) on 2026-09-06. Vocabulary: `CONTEXT.md` (Occurrence, Snoozed Occurrence, Snooze, Turn Off).

## The contract (`:core:alarm-scheduling:domain`)

```kotlin
data class Occurrence(val alarmId: AlarmId, val at: Instant, val kind: Kind) {
    enum class Kind { Regular, Snoozed }
}

fun interface TimeZoneProvider { fun current(): TimeZone }   // bound to TimeZone.currentSystemDefault()

/** Pure. Null when the Alarm is Disabled. Picks the Snoozed Occurrence when snoozedUntil is still ahead of now. */
fun nextOccurrence(alarm: Alarm, now: Instant, zone: TimeZone): Occurrence?

/** Pure; rules 2–4 without an Alarm. nextOccurrence delegates to it; Alarm Settings uses it for the draft countdown (docs/specs/alarm-settings.md). */
fun nextRegularOccurrence(time: AlarmTime, repeatDays: RepeatDays, now: Instant, zone: TimeZone): Instant

interface AlarmScheduler {
    /** Make the platform reflect this Alarm: Enabled → its next Regular Occurrence (and its Snoozed one, if any) is scheduled; Disabled → nothing is. Idempotent. */
    suspend fun sync(alarm: Alarm)
    /** Remove everything the platform holds for this Alarm (delete). */
    suspend fun cancel(id: AlarmId)
    /** Schedule the Snoozed Occurrence at `until`, replacing any pending one. */
    suspend fun snooze(id: AlarmId, until: Instant)
    /** Reconcile the repository with the platform; see "Sync". */
    suspend fun syncAll()
}
```

`Alarm` gains `snoozedUntil: Instant?`. `kotlin.time.Clock` supplies `now`; there is no clock module (ADR-0004).

### Implementations (`:component:alarm-scheduling:data`)

- **Android** (`AndroidAlarmScheduler`): `sync` computes the next Regular Occurrence with `nextOccurrence` and calls `setAlarmClock` under the Alarm's regular request code; a future `snoozedUntil` gets a second `setAlarmClock` under the Alarm's snooze request code. `snooze` uses the snooze request code only. When an Occurrence fires, the receiver first checks the repository (Alarm missing or Disabled → ignore), then re-arms the next Regular Occurrence of a repeating Alarm before starting the ringing service. The mechanics of receivers and the service are #11.
- **iOS** (`AlarmKitAlarmScheduler`): `sync` schedules the AlarmKit alarm with `.relative(AlarmTime, repeats = .weekly(RepeatDays) | .never)` under the Alarm's main AlarmKit id — **unless AlarmKit reports that id in `.countdown` or `.alerting`, in which case it is left untouched** (ADR-0007). If `snoozedUntil` is ahead of now and the main id is not in `.countdown` (the native countdown was lost), it additionally schedules a second AlarmKit alarm under the Alarm's snooze id with `.fixed(snoozedUntil)`, the same attributes and `postAlert: 300`; this is a repair path, not the normal snooze. `snooze` maps to `AlarmManager.countdown(id:)` only while the id is `.alerting` and is a no-op otherwise, so the same `SnoozeAlarm` use case serves the in-app Trigger and the secondary `LiveActivityIntent` (where AlarmKit has already started the countdown and the use case only records `snoozedUntil`). The bridge shape is ADR-0007.

## Invariants and rules

1. **At most one Regular and one Snoozed Occurrence per Alarm** are pending at any time. `TriggerRoute.Trigger(alarmId)` therefore needs no occurrence id; both kinds open the same Trigger.
2. **Next Regular Occurrence** is strictly after `now`, at second 00 of Alarm Time. A One-shot rings today if Alarm Time is still ahead, otherwise tomorrow (an Alarm set to the current minute goes to tomorrow). A repeating Alarm rings on the first date, today included, whose weekday is in Repeat Days and whose Alarm Time in `zone` is after `now`; the horizon is seven days.
3. **DST**: a non-existent local time (spring gap) rings at the shifted instant that `LocalDateTime.toInstant(zone)` resolves to; a duplicated local time (autumn overlap) rings at the first of the two. One ring either way.
4. **Time-zone travel**: Alarm Time is local, so 07:00 rings at 07:00 in the new zone. AlarmKit's `.relative` does this natively; Android needs `syncAll()` on `TIMEZONE_CHANGED`.
5. **Snoozed Occurrence** is at exactly the tap instant plus five minutes, seconds preserved. Snoozes are unlimited. While `snoozedUntil` is ahead of `now`, it is the Alarm's Next Occurrence, so the list countdown shows it.
6. **What cancels a pending Snoozed Occurrence**: any change to a *scheduling* field (Alarm Time, Repeat Days, Enabled) or deletion. Changes to *presentation* fields (Name, Ringtone, Volume, Vibrate) keep it. On Android it rings with the new settings (the service reads the Alarm when it fires); **on iOS the native countdown is never touched, so the new settings apply from the next Occurrence** (ADR-0007; the `.fixed` re-creation first written here is now a repair path only). Undo-delete restores the Alarm without its Snoozed Occurrence.
7. **How an Occurrence ends**: Turn Off; Snooze (a Snoozed Occurrence replaces it); a timeout of **five minutes** of unanswered ringing, treated as Turn Off (so a One-shot becomes Disabled); or a cancellation from rule 6. If a second Occurrence arrives while one is ringing, the newer one takes over the Trigger and the older is treated as Turned Off. No queue. Android only: on iOS AlarmKit owns the alert; whether a missed ring leaves a notification is #11.
8. **One-shot after ringing** becomes Disabled on Turn Off, on timeout, and after a Snoozed Turn Off (settled while charting). Where the platform stopped it without telling the app (hardware button on iOS), `syncAll()` catches up.

## Sync

`syncAll()` is the single repair path for every way the database and the platform can drift:

1. Read all Alarms from the repository.
2. For each Enabled Alarm, `sync(alarm)`: the next Regular Occurrence is scheduled, and the Snoozed one if `snoozedUntil` is ahead of `now`.
3. For each Disabled Alarm, ensure the platform holds nothing (both ids on iOS, both request codes on Android). On iOS, step 2 skips a main id that AlarmKit holds in `.countdown` or `.alerting` (ADR-0007).
4. Housekeeping: a `snoozedUntil` in the past is cleared. On iOS, an Enabled One-shot that AlarmKit no longer lists is set to Disabled.

It is idempotent, so it is called without checking whether anything drifted: on every app start on both platforms; on Android also from `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_CHANGED` and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (receiver declarations: #11); on iOS only on `UIApplicationDidBecomeActiveNotification` — which covers cold UI starts and foreground returns but not the background launches AlarmKit intents make (ADR-0007).

## Considered options

- **Occurrence-level contract** (`schedule(occurrence)` on both platforms, iOS via `.fixed(Date)`) — rejected: it loses AlarmKit's native weekly repeats and time-zone-relative semantics, and relies on the stop intent running after every ring to schedule the next one, which is unconfirmed for hardware-button stops.
- **Occurrence id in `TriggerRoute`** — rejected: with rule 1 and the repository check on fire, the id would carry no decision.
- **Snoozed Occurrence as its own entity and repository** — rejected: one nullable field gives the same guarantees, the list countdown falls out of `nextOccurrence`, and #15 gets a column instead of a table.
- **Presentation edits during snooze cancel it, or on iOS ring with the old settings** — rejected here in favour of re-creating the snooze as `.fixed(snoozedUntil)`; ADR-0007 later reversed the iOS half (edits apply from the next Occurrence) because `syncAll()` on every foreground return would otherwise cancel the native countdown.
- **Auto-snooze on timeout** — rejected in favour of a plain Turn Off after five minutes: simpler to reason about and to test; AOSP DeskClock behaves the same way (with ten minutes).
- **Per-event repair paths instead of `syncAll()`** — rejected: five special cases replaced by one idempotent operation whose cost is a handful of system calls per app start.

## Consequences

- `nextOccurrence` is pure and lives in `commonTest` TDD scope: rules 2–5 are its test cases, with `TimeZoneProvider` and `Clock` faked. Rules 2–4 are tested on `nextRegularOccurrence`, rule 5 on the wrapper.
- On iOS every Alarm maps to two AlarmKit ids (main and snooze), derived deterministically from `AlarmId` (a masked `Uuid`, ADR-0007); `syncAll()` must clean both.
- On Android every Alarm maps to two request codes; `cancel` and `sync` of a Disabled Alarm must clear both.
- The ringing service (#11) owns the five-minute timeout and the "newer takes over" rule; the Trigger ViewModel (#19) does not need to know which kind of Occurrence woke it.
- #15 persists `snoozedUntil` as a nullable column on the alarm row, not a second table.
