---
status: accepted
---

# Module map: two features, alarm concerns in core, platform delivery behind bridges

ADR-0002 committed to a multi-module layout; this ADR fixes the modules. The app is two features — `alarms` (Alarm List, Alarm Settings with the name dialog, Ringtone Setting) and `trigger` (the Alarm Trigger screen) — because the Trigger has its own entry path (a full-screen Activity on Android, the AlarmKit alert on iOS) and its own lifecycle. Everything both features need (`Alarm`, `Occurrence`, the repository and scheduler contracts) lives in `core`, so **no feature has a `:data` layer**: the implementations are components. Platform-only APIs are reached through Kotlin contracts implemented in the platform source set of a component, with AlarmKit (Swift-only) behind a thin `AlarmKitBridge` interface that `iosApp` implements and hands to `initApp`.

Decided in [Module map: core/component/feature boundaries and navigation graph (#9)](https://github.com/IgorJonski/Snoozeloo/issues/9) on 2026-09-06. Root package: `com.igoyon.snoozeloo`.

## Hosts

| Module | Holds |
| --- | --- |
| `:shared` (KMP, the `:app` role) | `commonMain`: `App()` — root `NavHost` on `RootRoute`; `TriggerApp()` — `NavHost` on `TriggerRoute`; `appModules`; `initApp(config)`; the `kotlin.time.Clock` binding (`Clock.System`). `iosMain`: `MainViewController(bridge: AlarmKitBridge)` which calls `initApp { modules(iosHostModule) }`. Exports the static framework `Shared` with `export(project(":component:alarm-scheduling:data"))` so `AlarmKitBridge` is visible in Swift under its own name. |
| `:androidApp` (Android application) | `SnoozelooApplication` → `initApp { androidContext(this); modules(androidHostModule) }` binding `TriggerIntentFactory`; `MainActivity` → `App()`; `AlarmTriggerActivity` → `TriggerApp()`; the manifest permissions from `docs/research/android-exact-alarms.md`; SplashScreen API theme. |
| `iosApp` (Xcode) | `iOSApp.swift`; `AlarmKitBridgeImpl.swift` — a thin implementation with no mapping logic; the `SnoozelooAlarmWidget` extension target (the Live Activity AlarmKit requires for the snooze countdown); LaunchScreen; Info.plist keys. |

The template names `:shared` and `:androidApp` are kept on purpose; `:shared` is the assembly module the `kmp-module-structure` skill calls `:app`.

## Core — contracts and models, no implementations

| Module | Holds | Depends on |
| --- | --- | --- |
| `:core:error-reporting:domain` | `ErrorReporter` | — |
| `:core:usecase:domain` | `UseCase`, `asResult` | `:core:error-reporting:domain` (`api`) |
| `:core:navigation:domain` | `RootRoute`, `TriggerRoute` | — |
| `:core:ringtone:domain` | `RingtoneId`, `Ringtone`, the catalog and player contracts (shape: #13) | — |
| `:core:alarm:domain` | `Alarm`, `AlarmId`, `AlarmTime`, `RepeatDays`, `Volume`, `AlarmRepository` | `:core:ringtone:domain` (`api`) |
| `:core:alarm-scheduling:domain` | `Occurrence`, next-Occurrence logic, `AlarmScheduler` (shape: #10; a `TimeZoneProvider` lands here if #10 needs one) | `:core:alarm:domain` (`api`) |
| `:core:permissions:domain` | the alarm-permissions contract (shape: #22) | — |

There is no `:core:clock:domain`: `kotlin.time.Clock` is already an interface, so it is injected directly.

## Component — implementations and shared building blocks

| Module | Holds | Depends on |
| --- | --- | --- |
| `:component:error-reporting:data` | `LoggingErrorReporter` (Kermit) | `:core:error-reporting:domain` |
| `:component:ui-lifecycle:presentation` | `ObserveAsEvents`, one-argument `dropUnlessResumed` | — |
| `:component:design-system:presentation` | theme, colours, Montserrat fonts, icons, shared composables (#14) | — |
| `:component:database:data` | Room database, entities, DAOs (#15) | — |
| `:component:alarm:data` | `DefaultAlarmRepository` over the DAO | `:core:alarm:domain`, `:component:database:data` |
| `:component:alarm-scheduling:data` | `commonMain`: `expect` Koin module. `androidMain`: `AndroidAlarmScheduler` (`setAlarmClock`), `AlarmReceiver`, `AlarmRingingService` (`systemExempted` FGS), `RescheduleReceiver` (ADR-0006; was `BootReceiver`), notification channels, library `AndroidManifest.xml`, the `TriggerIntentFactory` contract, the `AlarmRinger` implementation (ADR-0006). `iosMain`: the `AlarmKitBridge` interface (scheduling **and** authorization calls) and `AlarmKitAlarmScheduler`, which maps the domain model onto the bridge. | `:core:alarm-scheduling:domain`, `:core:alarm:domain` |
| `:component:ringtone:data` | Android: `RingtoneManager` catalog and player. iOS: bundled catalog and `AVAudioPlayer` (ObjC-callable, no bridge). Bundled sound files live in this module's `composeResources`. | `:core:ringtone:domain` |
| `:component:permissions:data` | Android: exact-alarm, full-screen-intent and notification checks. iOS: AlarmKit authorization delegated to `AlarmKitBridge`. | `:core:permissions:domain`; on iOS also `:component:alarm-scheduling:data` |

## Feature

| Module | Holds | Depends on |
| --- | --- | --- |
| `:feature:alarms:domain` | `ObserveAlarms`, `SaveAlarm`, `DeleteAlarm`, `RestoreAlarm` (undo), `SetAlarmEnabled` | `:core:alarm:domain`, `:core:alarm-scheduling:domain`, `:core:ringtone:domain`, `:core:usecase:domain` |
| `:feature:alarms:presentation` | Alarm List, Alarm Settings (with the name dialog), Ringtone Setting, their ViewModels, `alarmsGraph` | `:feature:alarms:domain`, `:core:navigation:domain`, `:core:permissions:domain`, `:component:design-system:presentation`, `:component:ui-lifecycle:presentation` |
| `:feature:trigger:domain` | ~~`TurnOffAlarm`, `SnoozeAlarm`~~ — superseded by ADR-0006: the use cases live in `:component:alarm-scheduling:domain`; this module is never created | — |
| `:feature:trigger:presentation` | Trigger screen, ViewModel, `triggerGraph` | `:component:alarm-scheduling:domain` (ADR-0006; was `:feature:trigger:domain`), `:core:navigation:domain`, `:component:design-system:presentation`, `:component:ui-lifecycle:presentation` |

Group rules are the `kmp-module-structure` ones: `core → core`, `component → core + component`, `feature → core + component`, hosts → everything. Test fixtures are not shared in a module; Mokkery mocks the interfaces.

## Routes (`:core:navigation:domain`)

```kotlin
sealed interface RootRoute {
    @Serializable data object AlarmList : RootRoute
    @Serializable data class AlarmSettings(val alarmId: String?) : RootRoute      // null = new alarm
    @Serializable data class RingtoneSetting(val ringtoneId: String?) : RootRoute // current selection
}

sealed interface TriggerRoute {
    @Serializable data class Trigger(val alarmId: String) : TriggerRoute          // #10 may add an occurrence id
}
```

- `alarmsGraph(navController)`: Alarm List → `AlarmSettings(id)` from a card, `AlarmSettings(null)` from the FAB; Alarm Settings → `RingtoneSetting(currentId)`; Ringtone Setting pops and returns `selectedRingtoneId` through `previousBackStackEntry.savedStateHandle`. The name dialog is screen state, not a route.
- `triggerGraph(onFinished)`: one destination, no controller; leaving the screen is a callback the host answers (`AlarmTriggerActivity.finish()` on Android; iOS per #12).
- Two route families because the Trigger never navigates to the list and vice versa; a shared family would only invite a stray `navigate(Trigger)`.

## Gradle

An included build `build-logic` carries three convention plugins — `snoozeloo.kmp.library` (KMP + `com.android.kotlin.multiplatform.library` + `iosArm64`/`iosSimulatorArm64`), `snoozeloo.kmp.compose` and `snoozeloo.kmp.room` (KSP + Room). Every version, library and plugin lives in `gradle/libs.versions.toml`.

## Considered options

- One `:feature:alarms` holding the Trigger too — rejected: different entry path and lifecycle; the split costs one extra graph.
- `Alarm` and `AlarmRepository` in the feature, Trigger fed a DTO — rejected: two features share the model, which is the skill's definition of `core`.
- Swift implementing `AlarmScheduler` directly — rejected: the domain-to-AlarmKit mapping would live in untestable Swift; a primitive bridge keeps Swift to a few dozen lines.
- A mutable `platformModule` set from Swift — rejected in favour of `initApp`'s config hook, which the `kmp-di-koin` skill prescribes for host bindings.
- Ringtone selection returned via a feature-scoped draft store — rejected: global state that must be cleared; the Settings ViewModel survives on the back stack, so only one id needs to travel back.

## Consequences

- `AlarmKitBridge` is the only Swift bridge; anything else AlarmKit-only (authorization included) is added to it rather than to a second bridge.
- `:shared` must `export` the bridge's module from the framework or Swift sees mangled names.
- Bundled sounds in `composeResources` land in the app's `compose-resources/` directory on iOS, while AlarmKit reads only the main bundle root or `Library/Sounds`; #13 decides the copy step.
- The `TriggerIntentFactory` indirection exists because the ringing service (a component) may not depend on the Activity (host) that renders a feature.
