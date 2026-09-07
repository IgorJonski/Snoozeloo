---
status: accepted
---

# iOS alarm delivery: AlarmKit behind a command bridge and an events entry point, native snooze countdown, Compose Trigger as a foreground overlay

On iOS the system owns the ring: AlarmKit schedules the whole Alarm, shows its own alert on the Lock Screen, performs Turn Off and Snooze itself and calls the app back through App Intents that launch the process *without* any UI. The app cannot replace or suppress that alert. We therefore treat the AlarmKit alert as the Trigger on iOS, keep the Compose Trigger only as an overlay for the case where the app is already in the foreground, let AlarmKit run the five-minute snooze countdown natively (Apple's paved path, hardware buttons included), and make every Kotlin-side operation **state-aware** so that it never fights the system: `stop`/`countdown` only act on an alerting alarm, and `sync` never touches an alarm that AlarmKit holds in `.countdown` or `.alerting`. Swift stays a few dozen lines: one class implementing the `AlarmKitBridge` commands, two `LiveActivityIntent`s that call one Kotlin `AlarmKitEvents` object, and the widget extension AlarmKit requires for the countdown.

Decided in [iOS alarm delivery: AlarmKit bridge and the Trigger experience (#12)](https://github.com/IgorJonski/Snoozeloo/issues/12) on 2026-09-06. Facts come from [`docs/research/alarmkit-capabilities.md`](https://github.com/IgorJonski/Snoozeloo/blob/research/alarmkit-capabilities/docs/research/alarmkit-capabilities.md) (#2); scheduling semantics from ADR-0005; the shared `TurnOffAlarm`/`SnoozeAlarm` use cases and `AlarmRinger` from ADR-0006. Vocabulary: `CONTEXT.md`.

## Sequence

1. **Arm.** `AlarmKitAlarmScheduler.sync(alarm)` reads `bridge.alarms()`. If the Alarm's main id is reported in `.countdown` or `.alerting`, it is left untouched. Otherwise the main id is (re)scheduled with `.relative(AlarmTime, .weekly(RepeatDays) | .never)`, `postAlert: 300`, title, sound and tint — re-issuing `schedule` under an existing id is how Name/Ringtone edits reach AlarmKit. A `snoozedUntil` ahead of `now` while the main id is *not* in `.countdown` (the native countdown was lost) gets a `.fixed(snoozedUntil)` alarm under the Alarm's snooze id, same attributes and `postAlert`; this is a repair path only. A Disabled Alarm has both ids cancelled.
2. **Alert.** AlarmKit shows its alert: title, app name, the system Stop control and one secondary button, **Snooze** (`secondaryButtonBehavior: .countdown`). The alert is the Trigger on iOS. Meanwhile Swift's `alarmUpdates` subscription calls `events.stateChanged(id, .alerting)`, which sets `AlarmRinger.ringing = alarmId`.
3. **Overlay.** If the app is in the foreground, `MainViewController()`'s root — `App()` always in composition, `TriggerApp()` drawn over it in a `Box` while `ringing != null` — shows the Compose Trigger. Its Turn Off / Snooze go through the same `TurnOffAlarm` / `SnoozeAlarm` use cases as on Android; the scheduler maps them to `bridge.stop(id)` / `bridge.countdown(id)`. The overlay disappears when `ringing` returns to `null`, whichever surface answered; `triggerGraph(onFinished)` is a no-op on iOS.
4. **Answer from the system alert.** Stop → the system stops the alarm, then performs `StopAlarmIntent`, whose `perform()` calls `events.stopped(id)` → `TurnOffAlarm(id)` (One-shot → Disabled, `snoozedUntil` cleared, `sync`, `ringer.stop` no-op because nothing is alerting). Snooze → the system starts the five-minute countdown itself, then performs `SnoozeAlarmIntent` → `events.snoozed(id)` → `SnoozeAlarm(id)` (`snoozedUntil = now + 5:00`; `scheduler.snooze` sees `.countdown` and does nothing; `ringer.stop` no-op). Both intents run in a background launch of the app process where no view controller exists, which is why Koin starts in `iOSApp.init` (see Host wiring).
5. **Snoozed ring.** The countdown elapses, AlarmKit alerts again under the same id; steps 2–4 repeat. Snoozes are unlimited.
6. **Repeat.** A `.weekly` alarm stays scheduled after Stop; AlarmKit computes the next weekday itself. `TurnOffAlarm`'s `sync(alarm)` re-issues `schedule` under the same id (harmless replacement; hardware-verification item below).
7. **Repair.** `syncAll()` (ADR-0005) runs on `UIApplicationDidBecomeActiveNotification`, observed by Kotlin in `iosMain` through `NSNotificationCenter` — never from `startApp`, so an intent-only background launch does not race `TurnOffAlarm`. It applies the step-1 rule per Alarm and flips an Enabled One-shot that `bridge.alarms()` no longer lists (fired and stopped by a hardware button, or by an intent that could not run) to Disabled.

## The bridge (`:component:alarm-scheduling:data`, `iosMain`)

```kotlin
/** Implemented in Swift (AlarmKitBridgeImpl.swift). No mapping logic: one AlarmKit call per method. */
interface AlarmKitBridge {
    fun authorizationState(): AlarmKitAuthorization                // NotDetermined | Authorized | Denied
    suspend fun requestAuthorization(): AlarmKitAuthorization
    suspend fun schedule(spec: AlarmKitSpec)
    suspend fun cancel(id: String)
    suspend fun stop(id: String)
    suspend fun countdown(id: String)
    suspend fun alarms(): List<AlarmKitAlarm>                      // AlarmManager.shared.alarms
}

data class AlarmKitSpec(
    val id: String,                    // UUID string
    val hour: Int, val minute: Int,    // .relative(Time)
    val weekdays: List<Int>,           // ISO 1–7; empty = repeats .never
    val fixedAtEpochSeconds: Long?,    // non-null → .fixed(Date) instead of .relative
    val title: String,                 // Name, or Alarm Time in the device format when Name is empty
    val soundName: String?,            // bundled file name (#13); null → .default
    val postAlertSeconds: Int,         // 300
    val tintHex: String,               // primary #4664FF
)

data class AlarmKitAlarm(val id: String, val state: AlarmKitState)
enum class AlarmKitState { Scheduled, Countdown, Alerting }
enum class AlarmKitAuthorization { NotDetermined, Authorized, Denied }
class AlarmKitException(message: String) : Exception(message)      // any AlarmManager throw, via the completion handler

/** Implemented in Kotlin, returned by startApp(bridge); Swift holds it in a global and calls it from intents and alarmUpdates. */
interface AlarmKitEvents {
    fun stopped(id: String)                                        // StopAlarmIntent → TurnOffAlarm
    fun snoozed(id: String)                                        // SnoozeAlarmIntent → SnoozeAlarm
    fun stateChanged(id: String, state: AlarmKitState?)            // alarmUpdates; null = no longer scheduled. Feeds AlarmRinger.ringing only.
}
```

Kotlin `suspend` crosses to Swift as completion handlers; the Swift implementation wraps each AlarmKit `async throws` call in a `Task` and reports errors through the handler, which surface in Kotlin as `AlarmKitException`. Only `String`, `Int`, `Long`, `List<Int>` and these data classes cross the boundary; no AlarmKit type does.

### State-aware rules (Kotlin side)

| Operation | Rule |
| --- | --- |
| `AlarmKitRinger.stop(id)` and `scheduler.snooze(id, until)` | query `alarms()`; act (`bridge.stop` / `bridge.countdown`) only when the id is `.alerting`, otherwise no-op. This is the answer to ADR-0006's open question: the system may already have stopped or snoozed the alarm, and the use cases must stay callable from every surface. |
| `scheduler.sync(alarm)` | as step 1: skip the main id when `.countdown`/`.alerting`; re-issue `schedule` otherwise. |
| `scheduler.cancel(id)` | `bridge.cancel` on both ids, unconditionally. |
| `AlarmRinger.ringing` | the id most recently reported `.alerting` by `stateChanged`; `null` once that id reports any other state or removal. `stateChanged` never runs a use case. |

### AlarmKit id pair

`AlarmId` is a `kotlin.uuid.Uuid` generated when the Alarm is created (persisted as its string form; constrains #15). The main AlarmKit id *is* the `AlarmId`; the snooze id is a pure function of it — the same 128 bits with a fixed mask applied to the low 64 bits — so `syncAll()` can clean both ids from the `AlarmId` alone and nothing is stored. Both go over the bridge as strings. On Android the two request codes derive from the same `AlarmId` (ADR-0005/0006).

## Host wiring

| Where | What |
| --- | --- |
| `:shared` `iosMain` | `fun startApp(bridge: AlarmKitBridge): AlarmKitEvents` — `initApp { modules(iosHostModule) }` with the bridge bound, returns the events object; **does not** call `syncAll()`. `fun MainViewController(): UIViewController` — no arguments; the root described in step 3. |
| `iosApp/iOSApp.swift` | `init()` calls `startApp(bridge:)` once and stores the returned `AlarmKitEvents` in a process-wide global that the intents read. Subscribes to `AlarmManager.shared.alarmUpdates` in a `Task` and forwards each alarm's state through `events.stateChanged`. |
| `iosApp/AlarmKitBridgeImpl.swift` | the `AlarmKitBridge` implementation; builds `AlarmConfiguration` with the 26.1 `Alert(title:secondaryButton:secondaryButtonBehavior:)` initializer, `CountdownDuration(preAlert: nil, postAlert:)`, `AlertSound.named(soundName)` or `.default`, `AlarmAttributes<SnoozelooMetadata>` with `tintColor`. |
| `iosApp/AlarmIntents.swift` | `StopAlarmIntent` and `SnoozeAlarmIntent` (`LiveActivityIntent`, `openAppWhenRun = false`) carrying the alarm id; `perform()` calls the matching `AlarmKitEvents` method and returns. They never call `stop`/`countdown` themselves. |
| `iosApp/SnoozelooAlarmWidget` (extension target) | pure SwiftUI, does not link `Shared`. `AlarmAttributes<SnoozelooMetadata>` (`Metadata = { alarmName: String }`) lives in one Swift file compiled into both targets. Renders the countdown state: Lock Screen / expanded = alarm name + `Text(timerInterval:)` to the fire date; compact Dynamic Island = the timer; minimal = an icon. No pause button, no custom font, tint = primary. |
| `Info.plist` | `NSAlarmKitUsageDescription` (non-empty), `NSSupportsLiveActivities`. No entitlement exists for AlarmKit. |
| `iosApp.xcodeproj` | `IPHONEOS_DEPLOYMENT_TARGET = 26.0`, Swift 6, `TEAM_ID` filled in (the device-setup task ticket). |

Authorization: the scheduler never prompts. A `sync` without authorization fails with `AlarmKitException`; `:component:permissions:data` (iOS) delegates `authorizationState()` / `requestAuthorization()` to the bridge and #22 decides when the app asks and what a `.denied` state shows.

## Behaviour matrix

| | Android (ADR-0006) | iOS (this ADR) |
| --- | --- | --- |
| Who rings | `AlarmRingingService` | AlarmKit |
| Trigger surface | full-screen `AlarmTriggerActivity`; heads-up with actions when in use | system alert (Lock Screen) / banner + Dynamic Island (unlocked); Compose Trigger only as an overlay when the app is in the foreground |
| Turn Off / Snooze entry points | Trigger screen, notification actions, timeout | system alert buttons, hardware buttons, Compose overlay |
| Snooze mechanism | app schedules `snoozedUntil` via `setAlarmClock` | AlarmKit native countdown (`postAlert: 300`); `.fixed` snooze alarm only as repair |
| Presentation edit during a snooze | rings with new settings | applies from the next Occurrence (ADR-0005 rule 6, iOS caveat) |
| Ringing timeout | five minutes, treated as Turn Off, `missed_alarms` notification | system-owned alert duration; no app timeout, no missed notification; a stopped One-shot is caught by `syncAll()` |
| Take-over by a newer Occurrence | service switches, `onNewIntent` | AlarmKit decides; `ringing` follows whatever it reports `.alerting` |
| Volume / Vibrate | applied by the service | no effect; both rows hidden in Alarm Settings via `AlarmCapabilities` |
| Silent Ringtone | no sound, vibration if enabled | bundled near-silent `.wav` (ADR-0008); haptics as the system decides |
| Repair trigger | `RescheduleReceiver` + app start | `didBecomeActive` only |
| Hardware needed to verify | no | yes (simulator alerts unreliable) |

`AlarmCapabilities` (`:core:alarm-scheduling:domain`, `expect`/`actual`): `hasVolume`, `hasVibrate` — `true`/`true` on Android, `false`/`false` on iOS. Alarm Settings (#17) reads it instead of checking the platform.

## Amends

- **ADR-0001** → `accepted`, with the degradations listed there.
- **ADR-0004**: `MainViewController(bridge:)` becomes `startApp(bridge): AlarmKitEvents` + `MainViewController()`; `AlarmKitEvents`, `AlarmKitRinger` and the foreground observer join `iosMain` of `:component:alarm-scheduling:data`; `AlarmCapabilities` joins `:core:alarm-scheduling:domain`; `AlarmId` is a `Uuid`; `iosApp` gains `AlarmIntents.swift`.
- **ADR-0005**: rule 6 gets the iOS caveat; the iOS `sync` and `snooze` paragraphs get the state-aware rule; `syncAll()` on iOS runs on `didBecomeActive` only.

## Considered options

- **Custom snooze** (`.custom` secondary button whose intent stops the alarm and schedules `.fixed(now + 5 min)`; no countdown presentation, so probably no widget extension) — kept as the fallback: it makes the model identical to Android and lets presentation edits apply during a snooze, but relies on undocumented behaviour (`stop` from inside a secondary intent, an alert without countdown) and `secondaryIntent` is unavailable before the first unlock after a reboot, where the native countdown still works.
- **No Compose Trigger on iOS** — rejected: the screen and ViewModel exist for Android, and the overlay costs one `Box` and a `collectAsState`.
- **Swift presenting the Trigger modally** — rejected: Swift would have to observe a Kotlin `StateFlow` and the root would lose navigation state; the overlay keeps `App()` in composition.
- **Kotlin passing a listener into the bridge (`setListener`) or a callback flow** — rejected in favour of a returned `AlarmKitEvents`: the intents need a Kotlin entry point that exists before any Compose root, and `startApp` is already the one call Swift makes at process start.
- **Two random AlarmKit ids persisted per Alarm** — rejected: a masked `Uuid` is a pure function and leaves nothing to migrate.
- **`sync` always re-creating the snooze as `.fixed` (ADR-0005 as first written)** — rejected on iOS: `syncAll()` on every foreground return would cancel the native countdown and its Live Activity each time the user opens the app during a snooze.
- **`syncAll()` inside `startApp`** — rejected: `startApp` also runs on intent-only background launches and would race the intent's `TurnOffAlarm`.
- **Non-`suspend` bridge with explicit callbacks** — rejected: Kotlin/Native already exports `suspend` as completion handlers, which is the shape Swift needs to wrap `async throws`.

## Consequences

- Swift in `iosApp`: `iOSApp.swift`, `AlarmKitBridgeImpl.swift`, `AlarmIntents.swift`, the widget extension and one shared attributes file. Nothing else; the rule of ADR-0003 holds.
- `:shared` exports `:component:alarm-scheduling:data` (ADR-0004) so `AlarmKitBridge`, `AlarmKitEvents`, `AlarmKitSpec` and friends appear in Swift under their own names.
- `TurnOffAlarm` and `SnoozeAlarm` need no platform branches: every iOS difference sits in `AlarmKitAlarmScheduler` and `AlarmKitRinger`, both unit-testable with a mocked `AlarmKitBridge` in `iosTest` (or in `commonTest` if the bridge types are moved up; implementation's call).
- On iOS a presentation edit during a snooze applies from the next Occurrence; the Alarm List still shows the correct countdown because `snoozedUntil` is recorded by `SnoozeAlarmIntent`.
- The five-minute ringing timeout, the `missed_alarms` notification and the take-over rule are Android-only. The Trigger ViewModel (#19) needs nothing beyond "close when `ringing` is no longer mine".
- `AlarmId` as `Uuid` is a constraint on #15 (text primary key, generated in the domain, not by Room).
- ADR-0008 supplies `soundName` per Ringtone (`Silent → "silent.wav"`, `Default → null`, bundled file otherwise) and copies the bundled `.wav` files into `Library/Sounds` on every `startApp`, because AlarmKit reads only the main bundle root or `Library/Sounds`.
- #17 hides Volume and Vibrate behind `AlarmCapabilities`. #22 owns the authorization moment and the `.denied` state. #20 gets the hardware-verification step from the device-setup task ticket.
- **Must be verified on a physical iPhone (iOS 26.1+) before the iOS delivery is considered done**: the alert fires from the Lock Screen and while unlocked; Snooze starts the countdown and the Live Activity renders; both intents reach `AlarmKitEvents` with the app killed; `schedule` under an existing id from inside `StopAlarmIntent` does not disturb a repeating alarm; a near-silent `.wav` is accepted as a sound, a custom `.wav` loops for the whole alert (or at least plays to its end) and `.default` is audible as an alarm; `alarmUpdates` reports `.alerting` while the app is in the foreground so the overlay appears.
