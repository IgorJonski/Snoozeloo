---
status: accepted
---

# Android alarm delivery: receiver → `systemExempted` ringing service → full-screen Trigger, with Turn Off and Snooze as shared use cases

Android hands the app a single `PendingIntent` at the Occurrence instant and nothing more; everything a user sees and hears from then on is the app's job. We deliver an Occurrence through the chain the platform sanctions for alarm clocks: `AlarmManager.setAlarmClock` → a three-line `BroadcastReceiver` → a `systemExempted` foreground service that rings and owns one high-importance notification whose full-screen intent opens `AlarmTriggerActivity`. Turn Off and Snooze can be requested from three places (the Trigger screen, the notification's action buttons, and the service's own five-minute timeout), so their rules live in shared use cases in a new `:component:alarm-scheduling:domain` module, not in the feature, and the platform side shrinks to a two-member `AlarmRinger` contract in core.

Decided in [Android alarm delivery: permissions, full-screen Trigger, ringing service (#11)](https://github.com/IgorJonski/Snoozeloo/issues/11) on 2026-09-06. Facts come from [`docs/research/android-exact-alarms.md`](https://github.com/IgorJonski/Snoozeloo/blob/research/android-exact-alarms/docs/research/android-exact-alarms.md) (#3); the scheduling semantics from ADR-0005. Vocabulary: `CONTEXT.md` (Occurrence, Trigger, Turn Off, Snooze).

## Sequence

1. **Arm.** `AndroidAlarmScheduler.sync(alarm)` (ADR-0005) calls `setAlarmClock(AlarmClockInfo(at, showIntent), alarmIntent)` for the next Regular Occurrence under the Alarm's regular request code, and for a future `snoozedUntil` under its snooze request code. `alarmIntent` is `PendingIntent.getBroadcast` to `AlarmReceiver` with the `alarmId` as an extra, `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`. `showIntent` opens `MainActivity` (the Alarm List); no deep link into Alarm Settings.
2. **Fire.** `AlarmReceiver.onReceive` does one thing: `ContextCompat.startForegroundService(AlarmRingingService, alarmId)`. No repository access, no `goAsync()`, never `startActivity()`.
3. **Ring.** `AlarmRingingService.onStartCommand`: `ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)` (type passed on API 34+ only) with the alarm notification; then reads the Alarm from the repository. Alarm missing or Disabled → `stopSelf()` in silence. Otherwise: for a repeating Alarm, re-arm its next Regular Occurrence (`scheduler.sync(alarm)`); publish `AlarmRinger.ringing = alarmId`; acquire a `PARTIAL_WAKE_LOCK` with a hard timeout of five minutes plus a margin; start the ringtone and vibration (how Volume and Vibrate are applied: #13); start the five-minute timeout as a coroutine in the service scope. Returns `START_REDELIVER_INTENT`, so a service the system kills mid-ring is restarted with the same Alarm (the timeout restarts too; accepted).
4. **Show.** The notification (channel `alarms`, `IMPORTANCE_HIGH`, channel sound off, `CATEGORY_ALARM`, `setOngoing(true)`) carries `setFullScreenIntent(fullScreen, true)`, a content intent equal to `fullScreen`, and two actions, **Turn Off** and **Snooze**, as `PendingIntent.getService` back to the ringing service. `fullScreen` comes from `TriggerIntentFactory.fullScreen(alarmId)`, bound in `:androidApp`, because the service may not know `AlarmTriggerActivity`. Locked or screen off → the system launches the Activity; device in use → a persistent heads-up with the two actions (Android 13+ behaviour). The notification's `deleteIntent` re-posts it: on Android 14+ a user can swipe an FGS notification away while unlocked, and the ringing must stay reachable.
5. **Answer.** Turn Off / Snooze from the Trigger screen go ViewModel → `TurnOffAlarm` / `SnoozeAlarm` use case. The same use cases run in the service for the notification actions and for the timeout (timeout = Turn Off, ADR-0005 rule 7). Both use cases end with `AlarmRinger.stop(alarmId)`, which stops the service. The service clears `ringing` and releases the wake lock on stop. The Trigger ViewModel observes `AlarmRinger.ringing` and closes the screen the moment the value is no longer its own `alarmId`; the Activity's `finish()` answers the `triggerGraph(onFinished)` callback of ADR-0004.
6. **Take-over.** A second Occurrence while one rings: `startForegroundService` reaches the running instance's `onStartCommand` with the new `alarmId`; the service runs `TurnOffAlarm(older)`, switches audio and vibration to the new Alarm and updates the notification. `AlarmTriggerActivity` is `singleInstance`, receives `onNewIntent`, and replaces its route with `Trigger(newId)` at host level. No queue, no timeout notification for the older Alarm.
7. **Timeout.** After five minutes unanswered the service runs `TurnOffAlarm(alarmId)` and posts one notification on channel `missed_alarms` (`IMPORTANCE_DEFAULT`, `autoCancel`, no actions, content intent = `MainActivity`). In domain terms this is a Turn Off by timeout; the user-facing copy is #19's and localisation's.
8. **Repair.** One `RescheduleReceiver` for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET` and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` calls `goAsync()` and `AlarmScheduler.syncAll()` (a few Room reads and `setAlarmClock` calls, no WorkManager, no foreground service). The receiver is always enabled. On Android 15+ the system re-delivers `BOOT_COMPLETED` when the user un-stops a force-stopped app, so that path needs nothing extra.

## `AlarmTriggerActivity`

Manifest: `exported="false"`, `showWhenLocked="true"`, `turnScreenOn="true"`, `excludeFromRecents="true"`, `launchMode="singleInstance"`, `taskAffinity=""`, its own theme without the splash. Turn Off and Snooze finish the Activity and leave the keyguard alone (no `requestDismissKeyguard`). Back does nothing. Home sends the Activity to the background; the ringing and the notification continue. `MainActivity`, on start, checks `AlarmRinger.ringing` and hands over to the Trigger when an Alarm is ringing, so opening the app from the launcher during a ring lands on the right screen.

## Manifest permissions (`:androidApp`)

| Permission | Why |
| --- | --- |
| `USE_EXACT_ALARM` | exact alarms on API 33+, install-time, alarm-clock apps only |
| `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) | exact alarms on Android 12/12L (minSdk 30), user-revocable there |
| `USE_FULL_SCREEN_INTENT` | Trigger over the lock screen; special app access on 14+ |
| `POST_NOTIFICATIONS` | the alarm notification on 13+ (runtime) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | the ringing service and its type |
| `RECEIVE_BOOT_COMPLETED` | `RescheduleReceiver` |
| `VIBRATE`, `WAKE_LOCK` | haptics and the partial wake lock while ringing |

Not requested: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `SYSTEM_ALERT_WINDOW`, `TURN_SCREEN_ON`, `FOREGROUND_SERVICE_SPECIAL_USE`. Runtime checks exist for `POST_NOTIFICATIONS` (33+), `canScheduleExactAlarms()` (31–32), `canUseFullScreenIntent()` (34+) and `areNotificationsEnabled()`; when they run and what the app shows is #22.

Fallback if Play review rejects `systemExempted` for a third-party alarm app: `foregroundServiceType="mediaPlayback"` with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, a one-line manifest change. Publishing is outside the map, so this is not decided now.

## Amends ADR-0004

Turn Off and Snooze have three callers, two of them inside `:component:alarm-scheduling:data`, and a component may not depend on a feature. The use cases therefore move down to a component `:domain` module (the `kmp-module-structure` layer for "the concern's shared use cases"), and the feature keeps only its presentation layer.

| Module | Holds | Depends on |
| --- | --- | --- |
| `:core:alarm-scheduling:domain` | as ADR-0005, plus `AlarmRinger` (below) | `:core:alarm:domain` (`api`) |
| `:component:alarm-scheduling:domain` (**new**) | `TurnOffAlarm`, `SnoozeAlarm`, `alarmSchedulingDomainModule` | `:core:alarm-scheduling:domain`, `:core:alarm:domain`, `:core:usecase:domain` |
| `:component:alarm-scheduling:data` | as ADR-0004, plus the `AlarmRinger` implementation and `RescheduleReceiver` (replacing `BootReceiver`); the service calls the use cases | ADR-0004's list plus `:component:alarm-scheduling:domain` |
| `:feature:trigger:domain` | **removed** — nothing left in it | — |
| `:feature:trigger:presentation` | as ADR-0004 | `:component:alarm-scheduling:domain` instead of `:feature:trigger:domain`; rest unchanged |

```kotlin
// :core:alarm-scheduling:domain
interface AlarmRinger {
    /** The Alarm ringing right now; null when silent. */
    val ringing: StateFlow<AlarmId?>
    /** Silence this Alarm if it is ringing; a no-op when it is not. */
    suspend fun stop(id: AlarmId)
}
```

`TurnOffAlarm(id)`: read the Alarm; One-shot → Disabled; clear `snoozedUntil`; `scheduler.sync(alarm)`; `ringer.stop(id)`. `SnoozeAlarm(id)`: `snoozedUntil = now + 5:00`; `scheduler.snooze(id, until)`; `ringer.stop(id)`. The no-op clause on `stop` matters on iOS, where `scheduler.snooze` maps to AlarmKit's `countdown(id:)`, which already silences the alert; `stop` after it must not cancel the countdown. How the iOS implementation tells the two states apart is #12.

## Considered options

- **Repository check and re-arm in the receiver (ADR-0005's wording)** — moved into the service: the receiver's temporary allow-list is short and Room is `suspend`; the service must enter the foreground within seconds anyway, and it already has a Koin scope and a coroutine scope. Semantics unchanged (missing or Disabled → silence).
- **Turn Off rules in the feature use case, duplicated in the service** — rejected: two copies of "One-shot becomes Disabled" drift apart.
- **`AlarmRinger` holding the business rules itself** (interface in core, rules in the `:data` implementation) — rejected in favour of component-domain use cases: rules belong in a domain module and test with mocked repository, scheduler and ringer; no wrapper use cases in the feature.
- **The service only signals; the ViewModel applies the rules** — rejected: in heads-up mode the Trigger screen may never exist, so the timeout would have no owner.
- **Take-over driven by the ViewModel observing `ringing`** — rejected for `onNewIntent` + route replacement; `ringing` is only used to close the screen.
- **`showIntent` deep-linking into `AlarmSettings(id)`** — rejected: a deep link in the root graph for little value.
- **`START_NOT_STICKY`** — rejected: a silently killed ringing service is the worst failure for an alarm clock.
- **A `BootReceiver` toggled with `setComponentEnabledSetting`** — rejected: `syncAll()` on a boot with no Enabled Alarms costs one query.
- **Direct boot** (ringing after a reboot before the first unlock: `directBootAware` receivers and device-protected storage for Room) — out of scope for this map; tracked in [Ring after a reboot before the first unlock (direct boot)](https://github.com/IgorJonski/Snoozeloo/issues/26).

## Consequences

- ADR-0004's module table is amended above; `:feature:trigger:domain` is never created.
- `AlarmScheduler.syncAll()` is invoked from `RescheduleReceiver`, `SnoozelooApplication` start (ADR-0005), and nowhere else on Android.
- After a reboot, alarms ring only once the user has unlocked the device for the first time, because `BOOT_COMPLETED` and the credential-encrypted database both wait for that unlock (#26).
- Two notification channels: `alarms` and `missed_alarms`; their display names are localisation's.
- Play Console declarations needed before publishing: exact-alarm sensitive permission, full-screen-intent core functionality = alarm, foreground service type `systemExempted` with the Android-14 wording ("apps holding `USE_EXACT_ALARM` […] to continue alarms in the background"). Publishing is out of scope.
- Verification that must happen on devices during implementation, not decidable here: the `systemExempted` manifest bit on API 30–33; `exported="false"` on the `BOOT_COMPLETED` receiver; the full-screen `PendingIntent` needing no background-activity-launch opt-in on API 36/37 (`StrictMode.detectBlockedBackgroundActivityLaunch()`).
- #19 (Trigger screen) gets: the screen closes on a `ringing` change, Back is a no-op, the two notification action labels, and the copy for the timeout notification. #22 gets the four runtime checks. #13 gets: audio and vibration start inside `AlarmRingingService` with `USAGE_ALARM` attributes.
