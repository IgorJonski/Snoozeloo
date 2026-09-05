# Android exact-alarm delivery and permissions (Android 11 – 17)

Resolves [#3](https://github.com/IgorJonski/Snoozeloo/issues/3). Researched 2026-09-05 against primary sources only (developer.android.com, Google Play policy pages on support.google.com, AOSP `AlarmManagerService.java`). Every claim carries its URL; anything not directly confirmed is marked **unconfirmed**.

Project context: KMP alarm-clock app, `minSdk = 30`, `targetSdk = 36` (`gradle/libs.versions.toml`). The app must ring exactly on time and show a full-screen Trigger screen over the lock screen.

---

## 1. Summary

1. **Exact alarms.** Declare `USE_EXACT_ALARM` (Android 13+, normal permission, granted at install, not user-revocable) because the app *is* an alarm clock — this is exactly the use case Google Play permits it for. Keep `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"` for Android 12/12L devices, where it is granted by default but user-revocable. [[perm-use-exact]](https://developer.android.com/reference/android/Manifest.permission#USE_EXACT_ALARM) [[play-perm]](https://support.google.com/googleplay/android-developer/answer/9888170)
2. **Scheduling API.** Use `AlarmManager.setAlarmClock(AlarmClockInfo, PendingIntent)`. It is the only API the system treats as a user-visible alarm clock: it fires in Doze *without* the while-idle throttling quota, wakes the device early, is exempt from App Standby bucket limits, surfaces the "next alarm" to the OS/quick settings, and explicitly "will be allowed to start a foreground service even if the app is in the background". Use `setExactAndAllowWhileIdle` only for non-alarm exact work (e.g. snooze bookkeeping is still an alarm, so use `setAlarmClock` there too). [[am-ref]](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)) [[aosp-ams]](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java)
3. **Ringing path.** Alarm `PendingIntent` → manifest `BroadcastReceiver` → `startForegroundService(RingingService)` (allowed from the background because an exact alarm is a documented exemption) → the service posts an `IMPORTANCE_HIGH`, `CATEGORY_ALARM` notification with `setFullScreenIntent(triggerPendingIntent, true)`. The system launches the Trigger activity when the device is locked/off and shows a persistent heads-up when the user is actively using the device. Never call `startActivity()` from the receiver or service. [[fgs-bg-start]](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) [[nb-fsi]](https://developer.android.com/reference/android/app/Notification.Builder#setFullScreenIntent(android.app.PendingIntent,%20boolean)) [[bc12]](https://developer.android.com/about/versions/12/behavior-changes-12)
4. **Foreground-service type.** Use `systemExempted` (permission `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`). The Android 14 docs list it explicitly for "Apps holding `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permission and are using Foreground Service to continue alarms in the background, including haptics-only alarms". It is not on the Android 15 `BOOT_COMPLETED` deny-list, has no timeout, and is not `shortService` (which Android 17 excludes for background audio). `mediaPlayback` is the fallback if Play review objects. [[fgs-types-14]](https://developer.android.com/about/versions/14/changes/fgs-types-required) [[fgs-types]](https://developer.android.com/develop/background-work/services/fgs/service-types)
5. **Full-screen intent.** `USE_FULL_SCREEN_INTENT` is a special app access on Android 14+; it is pre-granted only to apps whose core function is alarms or calls **and** which completed the Play Console "full-screen intent" declaration. Check `NotificationManager.canUseFullScreenIntent()` and deep-link to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` if false. Without it the alarm still surfaces as a 60-second heads-up over the lock screen. [[bc14]](https://developer.android.com/about/versions/14/behavior-changes-14#fsi) [[play-fgs-fsi]](https://support.google.com/googleplay/android-developer/answer/13392821)
6. **Notifications.** Request `POST_NOTIFICATIONS` (runtime, Android 13+) before the first alarm is saved. It is not required to *start* a foreground service, but a denied permission blocks the alarm notification (and therefore the full-screen intent) from being posted. [[notif-perm]](https://developer.android.com/develop/ui/views/notifications/notification-permission)
7. **Boot / lifecycle.** `RECEIVE_BOOT_COMPLETED` receiver re-schedules all enabled alarms; also handle `ACTION_MY_PACKAGE_REPLACED`, `ACTION_TIMEZONE_CHANGED`/`ACTION_TIME_CHANGED`, and `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Force-stop cancels every pending intent; on Android 15+ the system re-delivers `BOOT_COMPLETED` when the user brings the app out of the stopped state. [[alarms-guide]](https://developer.android.com/develop/background-work/services/alarms/schedule) [[bc15-all]](https://developer.android.com/about/versions/15/behavior-changes-all)
8. **Audio / haptics.** Play with `AudioAttributes.USAGE_ALARM`; vibrate with `VibrationAttributes.USAGE_ALARM` (required for background vibration). Android 17 background-audio hardening silently mutes playback and fails audio-focus requests from the background unless a (non-`shortService`) foreground service is running; when targeting API 37 the service additionally needs while-in-use capability **unless** the app holds the exact-alarm permission and is using `USAGE_ALARM` streams — which is precisely our configuration. [[a17-bg-audio]](https://developer.android.com/about/versions/17/changes/bg-audio)
9. **Android 17 (API 37)** is at platform stability (Beta 3, March 2026) and the docs already show QPR1/QPR2 betas, so it is the stable platform this app must run on. Changes that matter: background audio hardening (all apps), a listener-based `setExactAndAllowWhileIdle` variant, and sender-side BAL opt-in for `IntentSender.sendIntent()`. No changes to the exact-alarm permission model, full-screen intents, or the alarm-clock exemptions were found. [[a17-beta3]](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html) [[a17-all]](https://developer.android.com/about/versions/17/behavior-changes-all) [[a17-features]](https://developer.android.com/about/versions/17/features)
10. **Do not** request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `SYSTEM_ALERT_WINDOW`, or `TURN_SCREEN_ON`; none are needed for this path, and Play policy discourages the first. [[doze]](https://developer.android.com/training/monitoring-device-state/doze-standby)

---

## 2. Per-version matrix

| Topic | Android 11 (30) | Android 12 / 12L (31 / 32) | Android 13 (33) | Android 14 (34) | Android 15 (35) | Android 16 (36) | Android 17 (37) |
|---|---|---|---|---|---|---|---|
| **Exact-alarm permission** | None required. `canScheduleExactAlarms()` does not exist. | `SCHEDULE_EXACT_ALARM` introduced (special app access "Alarms & reminders"); required for `setExact*`/`setAlarmClock` by apps targeting 31+. **Granted by default**; user/system can revoke. [[bc12]](https://developer.android.com/about/versions/12/behavior-changes-12#exact-alarm-permission) | `USE_EXACT_ALARM` introduced: normal permission, auto-granted, not user-revocable, only for alarm/timer/calendar apps (Play policy). [[a13-features]](https://developer.android.com/about/versions/13/features#use-exact-alarm-permission) | `SCHEDULE_EXACT_ALARM` **denied by default** for fresh installs targeting 33+ (also after backup-restore; kept on OTA upgrade). `USE_EXACT_ALARM` holders unaffected. [[a14-exact]](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) | No change found. | No change found. | No change found. New `setExactAndAllowWhileIdle(type, ms, tag, Executor, OnAlarmListener)` variant (listener alarms are dropped when the app leaves its lifecycle — not for alarm clocks). [[a17-features]](https://developer.android.com/about/versions/17/features) |
| **`setAlarmClock` in Doze / standby** | Fires normally; system exits Doze shortly before. [[doze]](https://developer.android.com/training/monitoring-device-state/doze-standby) | Same; plus explicit FGS-start allowance from the alarm. [[am-ref]](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)) | Same | Same | Same | Same | Same |
| **Full-screen intent** | `USE_FULL_SCREEN_INTENT` normal permission (required since API 29). | Same | While the user is using the device the system shows a heads-up (with emphasised actions) instead of launching the intent. [[nb-fsi]](https://developer.android.com/reference/android/app/Notification.Builder#setFullScreenIntent(android.app.PendingIntent,%20boolean)) | Becomes **special app access**; pre-granted only for calling/alarm apps (Play declaration); `canUseFullScreenIntent()` + `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. Apps installed before the OTA keep it. [[bc14]](https://developer.android.com/about/versions/14/behavior-changes-14#fsi) | No change found. | No change found. | No change found. |
| **`POST_NOTIFICATIONS`** | N/A | N/A | Runtime permission introduced. FGS notices still visible in Task Manager when denied. [[notif-perm]](https://developer.android.com/develop/ui/views/notifications/notification-permission) | No change found. | No change found. | 36.1 adds `POST_PROMOTED_NOTIFICATIONS` (Live Updates, optional). [[perm-ref]](https://developer.android.com/reference/android/Manifest.permission#POST_PROMOTED_NOTIFICATIONS) | No change found. |
| **FGS types** | Only `camera`/`microphone` (and `location` since 10) must be declared. [[fgs-changes]](https://developer.android.com/develop/background-work/services/fgs/changes) | Same | Same; FGS shown in Task Manager. | **Type mandatory** for every FGS (else `MissingForegroundServiceTypeException`) plus per-type permission (else `SecurityException`). `systemExempted`, `specialUse`, `shortService` added. [[fgs-types-14]](https://developer.android.com/about/versions/14/changes/fgs-types-required) | `dataSync`/`mediaProcessing` 6 h/24 h timeouts. [[bc15]](https://developer.android.com/about/versions/15/behavior-changes-15) | Jobs running alongside an FGS now count against JobScheduler quota. [[bc16-all]](https://developer.android.com/about/versions/16/behavior-changes-all) | Background audio from an FGS requires the service to be non-`shortService`; targeting 37 also requires WIU capability or exact-alarm + `USAGE_ALARM`. [[a17-bg-audio]](https://developer.android.com/about/versions/17/changes/bg-audio) |
| **Starting an FGS from the background** | No restriction. | Restricted; exemptions include "Your app invokes an exact alarm to complete an action that the user requests" and `BOOT_COMPLETED`. [[fgs-bg-start]](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) | Same | While-in-use FGS (camera/mic/location/body sensors) cannot be *created* from the background even under an exemption. `BOOT_COMPLETED` may not start `microphone`. | `BOOT_COMPLETED` may not start `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone`. `SYSTEM_ALERT_WINDOW` exemption needs a visible overlay. [[bc15]](https://developer.android.com/about/versions/15/behavior-changes-15) | No change found. | No change found beyond the audio rule above. |
| **Background activity launch (BAL)** | Restricted since 10; full-screen-intent notifications are the sanctioned path. | Notification trampolines (activity started from a receiver/service after a notification tap) blocked; `PendingIntent` mutability flag mandatory. [[bc12]](https://developer.android.com/about/versions/12/behavior-changes-12) | Same | Sender must opt in (`pendingIntentBackgroundActivityStartMode`) when sending a `PendingIntent` from the background. [[bal]](https://developer.android.com/guide/components/activities/background-starts) | Creator must opt in too (`pendingIntentCreatorBackgroundActivityStartMode`). | `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` added; `StrictMode.detectBlockedBackgroundActivityLaunch()`. | `IntentSender.sendIntent()` also needs sender-side opt-in. |
| **Boot / stopped state** | `RECEIVE_BOOT_COMPLETED` (normal). | Same | Same | Manifest-declared broadcasts are not queued while cached (context-registered ones are). [[bc14-all]](https://developer.android.com/about/versions/14/behavior-changes-all) | Force-stop cancels all pending intents; `BOOT_COMPLETED` re-delivered when the user brings the app out of the stopped state. [[bc15-all]](https://developer.android.com/about/versions/15/behavior-changes-all) | Ordered-broadcast priority no longer cross-process (irrelevant here). | No change found. |
| **App Standby / restricted bucket** | Restricted bucket: one alarm per day — but alarm-clock alarms are exempt (AOSP `isExemptFromAppStandby`). | `SCHEDULE_EXACT_ALARM` holders targeting ≤33 stay in WORKING_SET or better. [[perm-ref]](https://developer.android.com/reference/android/Manifest.permission#SCHEDULE_EXACT_ALARM) | `USE_EXACT_ALARM` holders always stay in WORKING_SET or better. [[perm-use-exact]](https://developer.android.com/reference/android/Manifest.permission#USE_EXACT_ALARM) | Same | Same | Same | Same |

"No change found" means the relevant behaviour-changes pages for that release (all-apps and targeting pages) contain nothing on the topic; it is not a statement from Google.

---

## 3. Details per topic

### 3.1 `USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM`

**What each is**

- `SCHEDULE_EXACT_ALARM` (API 31): "a special access permission that can be revoked by the system or the user. It should only be used to enable user-facing features that require exact alarms. […] Apps need to target API `S` or above to be able to request this permission. Note that apps targeting lower API levels do not need this permission to use exact alarm APIs." — https://developer.android.com/reference/android/Manifest.permission#SCHEDULE_EXACT_ALARM
- `USE_EXACT_ALARM` (API 33): "Allows apps to use exact alarms just like with `SCHEDULE_EXACT_ALARM` but without needing to request this permission from the user. This is only intended for use by apps that rely on exact alarms for their core functionality. […] app stores may enforce policies to audit and review the use of this permission. […] Apps need to target API `TIRAMISU` or above […] only one of `USE_EXACT_ALARM` or `SCHEDULE_EXACT_ALARM` should be requested on a device. If your app is already using `SCHEDULE_EXACT_ALARM` on older SDKs but needs `USE_EXACT_ALARM` on SDK 33 and above, then `SCHEDULE_EXACT_ALARM` should be declared with a max-sdk attribute, like: `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />`" — https://developer.android.com/reference/android/Manifest.permission#USE_EXACT_ALARM
- Comparison table in the guide: `USE_EXACT_ALARM` = "Granted automatically / Cannot be revoked by the user / Subject to an upcoming Google Play policy / Limited use cases"; `SCHEDULE_EXACT_ALARM` = "Granted by the user / Broader set of use cases / Apps should confirm that the permission has not been revoked". — https://developer.android.com/develop/background-work/services/alarms/schedule#exact-alarm-permission

**When each is granted / revoked**

- Android 12/12L: `SCHEDULE_EXACT_ALARM` is pre-granted; user can revoke in *Special app access → Alarms & reminders*. "When the `SCHEDULE_EXACT_ALARM` permission is revoked for your app, your app stops, and all future exact alarms are canceled." — https://developer.android.com/develop/background-work/services/alarms/schedule#exact-alarm-permission ; "When the user revokes the `SCHEDULE_EXACT_ALARM` permission, all alarms scheduled with `setExact`, `setExactAndAllowWhileIdle` and `setAlarmClock` will be deleted." — https://developer.android.com/reference/android/app/AlarmManager#ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
- Android 14+: "`SCHEDULE_EXACT_ALARM` […] is no longer being pre-granted to most newly installed apps targeting Android 13 and higher (will be set to denied by default). If the user transfers app data to a device running Android 14 through a backup-and-restore operation, the permission will still be denied. If an existing app already has this permission, it'll be pre-granted when the device upgrades to Android 14." The change affects apps that "Isn't a calendar or alarm clock app"; "Calendar or alarm clock apps […] can request the `USE_EXACT_ALARM` normal permission. The `USE_EXACT_ALARM` permission will be granted on install". — https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Android 13 feature note: "it must satisfy at least one of the following criteria: Your app is an alarm clock app or a timer app. Your app is a calendar app that shows notifications for upcoming events." — https://developer.android.com/about/versions/13/features#use-exact-alarm-permission
- AOSP: `setImpl` throws `SecurityException("Caller … needs to hold SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM to set exact alarms.")` when neither is held and the app is not on the power allow-list; `hasUseExactAlarmInternal` is a plain permission check, `hasScheduleExactAlarmInternal` is an AppOps check with the deny-by-default compat change. — https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java (`setImpl`, `hasUseExactAlarmInternal`, `hasScheduleExactAlarmInternal`)
- OS-side "is an alarm clock app" detection does not exist as a separate signal: the OS only sees the `USE_EXACT_ALARM` declaration; eligibility is enforced by Play review.

**Google Play policy (alarm-clock apps)**

- "`USE_EXACT_ALARM` is a restricted permission and apps must only declare this permission if their core functionality supports the need for an exact alarm. Apps that request this restricted permission are subject to review, and those that do not meet the acceptable use case criteria will be disallowed from publishing on Google Play." Acceptable: "The app is an alarm or timer app. The app is a calendar app that shows event notifications." Do: "Complete Play Console declaration to indicate app functionality." — https://support.google.com/googleplay/android-developer/answer/9888170 (section *Exact Alarm Permission*)
- The April-2026 preview of that policy page keeps the exact-alarm and full-screen-intent sections unchanged (the dated changes there concern location/contacts, effective 2027-01-27). — https://support.google.com/googleplay/android-developer/answer/16909972

**Runtime handling**

- "call `canScheduleExactAlarms()` before trying to set an exact alarm" (API 31+; always true for `USE_EXACT_ALARM` holders on 33+). On grant the system sends `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`; the receiver should re-check `canScheduleExactAlarms()` and reschedule "similar to what your app does when it receives the `ACTION_BOOT_COMPLETED` broadcast". Send users to `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (with `package:` data URI; result `RESULT_OK` if granted). — https://developer.android.com/develop/background-work/services/alarms/schedule#exact-alarm-permission , https://developer.android.com/reference/android/provider/Settings#ACTION_REQUEST_SCHEDULE_EXACT_ALARM
- Exemptions that always allow exact alarms: platform-signed, privileged, and power-allow-listed apps. — https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- "If the exact alarm is set using an `OnAlarmListener` object, such as with the `setExact` API, the `SCHEDULE_EXACT_ALARM` permission isn't required." But "Starting with `UPSIDE_DOWN_CAKE`, the system will explicitly drop any alarms set via this API when the calling app goes out of lifecycle." — not usable for alarm clocks. — https://developer.android.com/reference/android/app/AlarmManager#setExact(int,%20long,%20java.lang.String,%20android.app.AlarmManager.OnAlarmListener,%20android.os.Handler)

**Decision for Snoozeloo:** declare `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`); still gate scheduling on `canScheduleExactAlarms()` on API 31–32 and keep a "permission revoked" UI state there; complete the Play Console sensitive-permission declaration.

### 3.2 `setAlarmClock` vs `setExactAndAllowWhileIdle` (and `AlarmClockInfo`)

- `setAlarmClock`: "Schedule an alarm that represents an alarm clock, which will be used to notify the user when it goes off. The expectation is that when this alarm triggers, the application will further wake up the device to tell the user about the alarm — turning on the screen, playing a sound, vibrating, etc. As such, the system will typically also use the information supplied here to tell the user about this upcoming alarm if appropriate. […] these alarms will be allowed to trigger even if the system is in a low-power idle (a.k.a. doze) mode. The system may also do some prep-work when it sees that such an alarm coming up […] This method is like `setExact`, but implies `RTC_WAKEUP`. […] Alarms scheduled via this API will be allowed to start a foreground service even if the app is in the background." — https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)
- Guide: "Invoke an alarm at a precise time in the future. Because these alarms are highly visible to users, the system never adjusts their delivery time. The system identifies these alarms as the most critical ones and leaves low-power modes if necessary to deliver the alarms." — https://developer.android.com/develop/background-work/services/alarms/schedule#exact-alarm-methods
- `setExactAndAllowWhileIdle`: "this alarm will be allowed to execute even when the system is in low-power idle modes. […] When the alarm is dispatched, the app will also be added to the system's temporary power exemption list for approximately 10 seconds […] To reduce abuse, there are restrictions on how frequently these alarms will go off for a particular application. Under normal system operation, it will not dispatch these alarms more than about every minute […]; when in low-power idle modes this duration may be significantly longer, such as 15 minutes." It too "will be allowed to start a foreground service even if the app is in the background." — https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent) ; Doze page: "Neither `setAndAllowWhileIdle()` nor `setExactAndAllowWhileIdle()` can fire alarms more than once per nine minutes, per app." — https://developer.android.com/training/monitoring-device-state/doze-standby
- AOSP confirms the difference: for `alarmClock != null` the service sets `FLAG_WAKE_FROM_IDLE` and `windowLength = 0`; `isExemptFromAppStandby(a)` returns true when `a.alarmClock != null`; while-idle alarms are quota-limited (`DEFAULT_ALLOW_WHILE_IDLE_QUOTA = 72` per hour with permission, `…_COMPAT_QUOTA = 7` per hour without), whereas alarm-clock alarms are not on that path. Exact alarms from alarm clocks get `mOptsWithFgsForAlarmClock` (temporary allow-list with `REASON_ALARM_MANAGER_ALARM_CLOCK`, `TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_ALLOWED`). — https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java (`setImpl`, `isExemptFromAppStandby`, `Constants`)
- Power-details table: "While-idle alarms: Limited to 7 per hour" under battery saver / restricted; restricted bucket "One alarm per day, either an exact alarm or an inexact alarm". — https://developer.android.com/topic/performance/power/power-details . Alarm-clock alarms are exempt per the AOSP check above (**doc-level confirmation of the bucket exemption not found; AOSP only**).
- `AlarmClockInfo(triggerTime, showIntent)`: "`showIntent`: an intent that can be used to show or edit details of the alarm clock." `getNextAlarmClock()` "Gets information about the next alarm clock currently scheduled. The alarm clocks considered are those scheduled by any application using the `setAlarmClock` method." — https://developer.android.com/reference/android/app/AlarmManager.AlarmClockInfo , https://developer.android.com/reference/android/app/AlarmManager#getNextAlarmClock() . The status-bar alarm icon / quick-settings "next alarm" line is System UI's consumer of this (the reference says "the system will typically also use the information supplied here to tell the user about this upcoming alarm if appropriate"; the exact UI is **not specified in docs**). `showIntent` should be a `PendingIntent.getActivity` for the alarm's edit screen (immutable is fine).
- All three exact APIs require the exact-alarm permission on 31+; `setExact` alone is deferred in Doze and should not be used for ringing. — https://developer.android.com/about/versions/14/changes/schedule-exact-alarms

**Decision:** `setAlarmClock` for every ring (initial and snooze); `RTC_WAKEUP` is implied. Re-schedule the next occurrence from the ring receiver, not with `setRepeating`.

### 3.3 `USE_FULL_SCREEN_INTENT` (Android 14+ grant rules, Play declaration)

- Reference: "Required for apps targeting `Q` that want to use notification full screen intents. Protection level: normal" (the constant's reference text still says normal; behaviourally it is special app access from 34). — https://developer.android.com/reference/android/Manifest.permission#USE_FULL_SCREEN_INTENT
- Android 14: "For apps targeting Android 14 (API level 34) or higher, apps that are allowed to use this permission are limited to those that provide calling and alarms only. […] This permission remains enabled for apps installed on the phone before the user updates to Android 14. Users can turn this permission on and off. […] You can use the new API `NotificationManager.canUseFullScreenIntent()` to check if your app has the permission; if not, your app can use the new intent `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` to launch the settings page where users can grant the permission." — https://developer.android.com/about/versions/14/behavior-changes-14#fsi
- `canUseFullScreenIntent()`: "From `UPSIDE_DOWN_CAKE`, apps may not have permission to use `USE_FULL_SCREEN_INTENT`. If permission is denied, notification will show up as an expanded heads up notification on lockscreen." — https://developer.android.com/reference/android/app/NotificationManager#canUseFullScreenIntent()
- `setFullScreenIntent`: "Only for use with extremely high-priority notifications demanding the user's immediate attention, such as an incoming phone call or alarm clock that the user has explicitly set to a particular time. […] From `TIRAMISU`, the system UI will display a heads up notification, instead of launching this intent, while the user is using the device. […] If the posting app holds `USE_FULL_SCREEN_INTENT`, then the heads up notification will appear persistently until the user dismisses or snoozes it, or the app cancels it. If the posting app does not hold `USE_FULL_SCREEN_INTENT`, then the notification will appear as heads up notification even when the screen is locked or turned off, and this notification will only be persistent for 60 seconds. To be launched as a full screen intent, the notification must also be posted to a channel with importance level set to `IMPORTANCE_HIGH` or higher." — https://developer.android.com/reference/android/app/Notification.Builder#setFullScreenIntent(android.app.PendingIntent,%20boolean)
- Time-sensitive guide: "If the user's device is locked, a full-screen activity appears, covering the lockscreen. If the user's device is unlocked, the notification appears in an expanded form". Channel example uses `IMPORTANCE_HIGH`. — https://developer.android.com/develop/ui/views/notifications/time-sensitive , https://developer.android.com/develop/ui/views/notifications/build-notification#urgent-message
- Play policy: "For apps targeting Android 14 (API target level 34) and above, `USE_FULL_SCREEN_INTENT` is a special apps access permission. Apps will only be automatically granted to use the `USE_FULL_SCREEN_INTENT` permission if the core functionality of their app falls under one of the below categories […]: setting an alarm; receiving phone or video calls." — https://support.google.com/googleplay/android-developer/answer/9888170 (section *Full-Screen Intent Permission*). Help Center: "If you use the `USE_FULL_SCREEN_INTENT` permission, you are required to complete the Play Console declaration starting May 31, 2024 […] Starting January 22, 2025, for apps targeting Android 14+, only apps that have calling or alarm functionalities will have this permission enabled by default. […] you'll have the option to declare that your app is a core functionality app for full-screen intent on the App content page (Monitor and improve > App content) in Play Console." — https://support.google.com/googleplay/android-developer/answer/13392821 . The 2026 preview of that page repeats the same FSI rules. — https://support.google.com/googleplay/android-developer/answer/16965181
- `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`: "the intent's data URI must specify the application package name". — https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT

**Decision:** declare the permission, complete the Play "full-screen intent" core-functionality declaration, check `canUseFullScreenIntent()` (API 34+) in onboarding/alarm-save flow and offer the settings deep link; treat the 60-second heads-up as the degraded mode (the ringing service keeps ringing regardless).

### 3.4 `POST_NOTIFICATIONS`

- "Android 13 (API level 33) and higher supports a runtime permission for sending non-exempt (including Foreground Services (FGS)) notifications from an app: `POST_NOTIFICATIONS`." "Apps don't need to request the `POST_NOTIFICATIONS` permission in order to launch a foreground service. However, apps must include a notification when they start a foreground service". "If the user selects the don't allow option, your app can't send notifications unless it qualifies for an exemption." Exemptions are media sessions and self-managed call apps only — alarms are **not** exempt. "if the user denies the notification permission, they still see notices related to foreground services in the Task Manager but don't see them in the notification drawer." — https://developer.android.com/develop/ui/views/notifications/notification-permission
- Consequence: a denied `POST_NOTIFICATIONS` means the alarm notification (and its full-screen intent) is not shown; the FGS still runs and can play audio. Check `areNotificationsEnabled()` before scheduling and surface a warning. — https://developer.android.com/reference/android/app/NotificationManager#areNotificationsEnabled()
- Because `targetSdk ≥ 33`, "your app has complete control over when the permission dialog is displayed"; prompt in context (e.g. when the user saves the first alarm). — same page.

### 3.5 Foreground service for ringing

**Why an FGS at all:** the receiver has ~10 s of temporary allow-list and no UI; ringing must survive until the user acts, and on Android 17 background audio requires an FGS (see 3.10). A `WAKE_LOCK` (`PARTIAL_WAKE_LOCK`) inside the service keeps the CPU up while the tone loops.

**Which type is legal**

- Android 14 requires a type: "If an app that targets Android 14 doesn't define types for a given service in the manifest, then the system will raise `MissingForegroundServiceTypeException`". — https://developer.android.com/about/versions/14/changes/fgs-types-required
- `systemExempted` — "Reserved for system applications and specific system integrations […] To use this type, an app must meet at least one of the following criteria: […] Apps holding `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permission […] Otherwise, declaring this type causes the system to throw a `ForegroundServiceTypeNotAllowedException`." — https://developer.android.com/develop/background-work/services/fgs/service-types#system-exempted . The Android-14 change page phrases the criterion as "Apps holding `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permission and are using Foreground Service to continue alarms in the background, including haptics-only alarms." — https://developer.android.com/about/versions/14/changes/fgs-types-required . Permission: `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` ("Apps are allowed to use this type only in the use cases listed in `ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED`", normal). — https://developer.android.com/reference/android/Manifest.permission#FOREGROUND_SERVICE_SYSTEM_EXEMPTED . No timeout; not on the Android 15 `BOOT_COMPLETED` deny-list.
- `mediaPlayback` — "Continue audio or video playback from the background." No timeout. Android 15+: "not allowed to launch a media playback foreground service from a `BOOT_COMPLETED` broadcast receiver" (irrelevant: we never ring from boot). Viable fallback; semantically it is for media, and Play's use-case list maps it to "Media Playback / Picture in Picture". — https://developer.android.com/develop/background-work/services/fgs/service-types#media , https://support.google.com/googleplay/android-developer/answer/13392821
- `specialUse` — requires a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation reviewed in Play Console; "Covers any valid foreground service use cases that aren't covered by the other foreground service types." Unnecessary since `systemExempted` covers alarms. — https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- `shortService` — 3-minute cap, "Cannot start other foreground services", and Android 17 background-audio rules require an FGS "that is not of type `SHORT_SERVICE`". Not suitable. — https://developer.android.com/develop/background-work/services/fgs/service-types#short-service , https://developer.android.com/about/versions/17/changes/bg-audio
- Play Console: "When your apps target Android 14 and above, you'll need to declare any foreground service types that you use in a new declaration on the App content page" with description, user impact, and a demo video. The pre-set use-case table does not list `systemExempted`; "if you do not see your use case listed, you can enter your use case manually." **Unconfirmed:** how Play review treats a `systemExempted` declaration from a non-system app — the Android docs explicitly enumerate exact-alarm holders, so the manual use-case text should quote that. — https://support.google.com/googleplay/android-developer/answer/13392821 , https://developer.android.com/develop/background-work/services/fgs/service-types#play-policy
- Type passed at runtime must be declared in the manifest: "If you pass a foreground service type to `startForeground` that you did not declare in the manifest, the system throws `IllegalArgumentException`." — https://developer.android.com/develop/background-work/services/fgs/launch . On API 30–33 the `systemExempted` bit is unknown to the platform; use `ServiceCompat.startForeground` and pass the type only on API 34+ (**verify on an API 30 emulator**).

**Starting the FGS from a BroadcastReceiver / exact alarm (Android 12–17)**

- Android 12+ background-start restriction; exemption: "Your app invokes an exact alarm to complete an action that the user requests." — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#background-start-restriction-exemptions ; guide: "Android considers exact alarms to be critical, time-sensitive interruptions. For this reason, exact alarms aren't affected by foreground service launch restrictions." — https://developer.android.com/develop/background-work/services/alarms/schedule#exact-alarm-methods ; `setAlarmClock`/`setExactAndAllowWhileIdle` javadoc: "will be allowed to start a foreground service even if the app is in the background." AOSP wires this via `BroadcastOptions.setTemporaryAppAllowlist(ALLOW_WHILE_IDLE_WHITELIST_DURATION, TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_ALLOWED, REASON_ALARM_MANAGER_ALARM_CLOCK)` — so the allowance is time-boxed; call `startForegroundService()` synchronously in `onReceive`.
- Android 14: while-in-use types (camera/mic/location/body sensors) cannot be created from the background even under an exemption — not relevant to `systemExempted`/`mediaPlayback`. — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions
- Android 15: only the `BOOT_COMPLETED` path is further restricted (list in matrix); alarm-triggered starts are untouched. `SYSTEM_ALERT_WINDOW` exemption narrowed (we do not use it). — https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
- Android 16: no FGS start changes; jobs run during an FGS now count against JobScheduler quota. — https://developer.android.com/about/versions/16/behavior-changes-all#jobscheduler-quota
- Android 17: an FGS started from an exact alarm typically has **no** while-in-use capability ("Background-Started FGS (BFSL): Most do not grant WIU access. The primary exceptions […] are interactions involving explicit user intent for example, notification clicks, widget interactions"). For apps targeting 37 this would block audio — except "the requirement for WIU capabilities is waived if the app has been granted the exact alarm permission, and it is making changes to audio streams that have the `USAGE_ALARM` attribute." — https://developer.android.com/about/versions/17/changes/bg-audio
- Notification for the FGS: "must use a priority of `PRIORITY_LOW` or higher"; we reuse the alarm notification itself (high importance, full-screen intent) as the FGS notification. — https://developer.android.com/develop/background-work/services/fgs/launch
- Since Android 14 users can swipe away ongoing notifications except "When the phone is locked"; the service must not rely on the notification staying visible. — https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications

### 3.6 `RECEIVE_BOOT_COMPLETED` and rescheduling

- Alarms do not survive reboot; "Set the `RECEIVE_BOOT_COMPLETED` permission […] (this only works if the app has already been launched by the user at least once)". The doc's pattern of `android:enabled="false"` + `setComponentEnabledSetting` is optional (avoids boot work when no alarm is enabled). — https://developer.android.com/develop/background-work/services/alarms/schedule#boot
- `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` and `TIMEZONE_CHANGED`/`TIME_CHANGED`/`LOCALE_CHANGED` receivers are themselves FGS-start exemptions (not needed: rescheduling is a few `setAlarmClock` calls, no FGS). — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#background-start-restriction-exemptions
- Also reschedule on `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31–32 devices). — https://developer.android.com/reference/android/app/AlarmManager#ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
- Android 15: "beginning with Android 15, the system cancels all an app's pending intents when the app is force-stopped" and "When the user's actions remove the app from the stopped state, the `ACTION_BOOT_COMPLETED` broadcast is delivered to the app providing an opportunity to re-register any pending intents." — https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state
- Manifest-declared receivers are delivered even when the app is cached ("Manifest-declared broadcasts aren't queued, and apps are removed from the cached state for broadcast delivery"). — https://developer.android.com/about/versions/14/behavior-changes-all#queued-broadcasts
- Direct boot: to ring before first unlock the receiver would need `android:directBootAware="true"` and device-protected storage for the alarm store. **Design choice, not researched further**; `LOCKED_BOOT_COMPLETED` is listed as an exemption above.
- Time-zone changes: `RTC_WAKEUP` alarms are epoch-based, so "07:00 local" must be recomputed on `ACTION_TIMEZONE_CHANGED`/`ACTION_TIME_CHANGED` (**inferred from RTC semantics; no doc sentence found stating AlarmManager re-derives local times**).

### 3.7 Doze / App Standby / battery saver

- Doze "Defers standard `AlarmManager` alarms, including `setExact()` and `setWindow()`, to the next maintenance window", "Alarms set with `setAlarmClock()` continue to fire normally. The system exits Doze shortly before those alarms fire.", "Ignores wake locks" (except during the post-alarm temporary allow-list). — https://developer.android.com/training/monitoring-device-state/doze-standby
- Battery-optimisation exemption: "Google Play policies prohibit apps from requesting direct exemption from Power Management features—Doze and App Standby—in Android 6.0 and above unless the core function of the app is adversely affected." The acceptable-use table has no alarm-clock row; `setAlarmClock` makes it unnecessary. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: "most applications should not use this". — same page; https://developer.android.com/reference/android/provider/Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- App Standby buckets: restricted bucket "Your app can invoke one alarm per day"; but `USE_EXACT_ALARM` holders "always stay in the `WORKING_SET` or lower standby bucket" and AOSP exempts alarm-clock alarms from standby deferral entirely. — https://developer.android.com/topic/performance/appstandby , https://developer.android.com/reference/android/Manifest.permission#USE_EXACT_ALARM , AOSP `isExemptFromAppStandby`
- Battery saver: AOSP `getMinDelivery…` path treats `FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED`/`FLAG_WAKE_FROM_IDLE` alarms as unrestricted; while-idle alarms are quota-limited. (**AOSP only; power-details page lists "While-idle alarms: Limited to 7 per hour".**)
- Android 14 cached-app enforcement: "Shortly after an app process enters a cached state, background work is disallowed" — not an issue for alarm delivery because the broadcast un-caches the process, and the FGS is started immediately. — https://developer.android.com/about/versions/14/behavior-changes-all#cached-apps-resource-usage

### 3.8 Trigger activity: `showWhenLocked` / `turnScreenOn` / keyguard / BAL

- `android:showWhenLocked`: "Specifies whether an Activity should be shown on top of the lock screen whenever the lockscreen is up and the activity is resumed. […] This should be used instead of `WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED` flag set for Windows. When using the Window flag during activity startup, there may not be time to add it before the system stops your activity for being behind the lock-screen." — https://developer.android.com/reference/android/R.attr#showWhenLocked
- `android:turnScreenOn`: "the activity will cause the screen to turn on if the activity will be visible and resumed due to the screen coming on. […] normally used in conjunction with the `showWhenLocked` flag […] if this flag is set and the activity calls `KeyguardManager.requestDismissKeyguard` the screen will turn on." Runtime equivalents `Activity.setShowWhenLocked(true)` / `setTurnScreenOn(true)` (API 27) exist but the manifest attributes are preferred for launch-time correctness. — https://developer.android.com/reference/android/R.attr#turnScreenOn , https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean)
- `KeyguardManager.requestDismissKeyguard(activity, callback)`: "If the Keyguard is not secure or the device is currently in a trusted state, calling this method will immediately dismiss the Keyguard without any user interaction. If the Keyguard is secure […] this will bring up the UI so the user can enter their credentials." Use only when the user chooses an action that leads into the app (e.g. "open"); Dismiss/Snooze should finish the activity and leave the keyguard alone. — https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,%20android.app.KeyguardManager.KeyguardDismissCallback)
- `TURN_SCREEN_ON` permission is "Intended to only be used by home automation apps" — not needed. — https://developer.android.com/reference/android/Manifest.permission#TURN_SCREEN_ON
- BAL: "The activity is started from a `PendingIntent` that was sent by the system (for example, from a notification tap)" is an allowed launch; the full-screen intent is exactly that. Do not `startActivity` from the receiver/service (Android 10 BAL block; Android 12 "notification trampoline" block: "your app cannot call `startActivity()` inside of a service or broadcast receiver"). The full-screen `PendingIntent` needs no BAL opt-in because System UI, not the app, sends it (**unconfirmed in docs; the BAL page does not mention full-screen intents at all**). For `PendingIntent`s the app sends itself, use `FLAG_IMMUTABLE` (mandatory mutability flag since 12). — https://developer.android.com/guide/components/activities/background-starts , https://developer.android.com/about/versions/12/behavior-changes-12#notification-trampolines
- Activity flags to use: `excludeFromRecents="true"`, `launchMode="singleInstance"` (one Trigger task, re-delivered on `onNewIntent` when a second alarm fires), `taskAffinity=""`, `exported="false"`, `noHistory` is optional. (**Best-practice choices, not doc requirements.**)

### 3.9 Audio focus, vibration, DND while ringing

- `AudioAttributes.USAGE_ALARM`: "Usage value to use when the usage is an alarm (e.g. wake-up alarm)." Routes to the alarm stream/volume. — https://developer.android.com/reference/android/media/AudioAttributes#USAGE_ALARM
- Audio focus: request with `AudioFocusRequest` using the same `USAGE_ALARM` attributes; `AUDIOFOCUS_GAIN_TRANSIENT` ("expect to play audio for only a short time and you expect the previous holder to pause") or `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`. "In Android 12 (API level 31) and higher, the system enforces this behavior. When an app requests audio focus while another app has the focus and is playing, the system forces the playing app to fade out." The guide's obligation to pause on `AUDIOFOCUS_LOSS` targets media apps; an alarm should keep ringing through transient losses and abandon focus when dismissed (**judgement; the guide does not discuss alarms**). — https://developer.android.com/media/optimize/audio-focus
- Android 17: audio playback, `requestAudioFocus`, and volume APIs called "while the app is not in a valid lifecycle […] fail silently without throwing an exception"; focus returns `AUDIOFOCUS_REQUEST_FAILED`. Valid lifecycle = visible activity or running non-`shortService` FGS; targeting 37 adds the WIU-or-(exact-alarm + `USAGE_ALARM`) rule. Test with `adb shell cmd audio set-enable-hardening throw` and look for `AudioHardening` in `dumpsys audio`/logcat. — https://developer.android.com/about/versions/17/changes/bg-audio
- Vibration: `Vibrator.vibrate(VibrationEffect, VibrationAttributes)` — "The app should be in the foreground for the vibration to happen. Background apps should specify a ringtone, notification or alarm usage in order to vibrate." `VibrationAttributes.USAGE_ALARM` = "Usage value to use for alarm vibrations." Requires `VIBRATE`. On API 31+ obtain the vibrator via `VibratorManager.getDefaultVibrator()`. — https://developer.android.com/reference/android/os/Vibrator#vibrate(android.os.VibrationEffect,%20android.os.VibrationAttributes) , https://developer.android.com/reference/android/os/VibrationAttributes#USAGE_ALARM
- Do Not Disturb: alarms are a distinct priority category (`NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS`, "Alarms are prioritized"); whether the user's DND policy lets them through is user-controlled, and the alarm stream is the right stream so the OS can apply that policy. — https://developer.android.com/reference/android/app/NotificationManager.Policy#PRIORITY_CATEGORY_ALARMS
- Notification: `setCategory(CATEGORY_ALARM)` ("Notification category: alarm or timer"), channel with `IMPORTANCE_HIGH`, sound disabled on the channel (the service plays audio itself), `setOngoing(true)`, `FLAG_INSISTENT` optional. — https://developer.android.com/reference/android/app/Notification#CATEGORY_ALARM

### 3.10 New in Android 16 and 17

**Android 16 (API 36 / 36.1)** — nothing alarm-specific. Relevant: JobScheduler quotas now apply to jobs running alongside an FGS (do not run WorkManager work from the ringing service); ordered-broadcast priority is per-process only; intent-redirect hardening; `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`; `StrictMode.detectBlockedBackgroundActivityLaunch()`; progress-centric notifications and, in 36.1, promoted "Live Update" notifications (`POST_PROMOTED_NOTIFICATIONS`) — optional polish for the pre-alarm countdown. — https://developer.android.com/about/versions/16/behavior-changes-all , https://developer.android.com/about/versions/16/behavior-changes-16 , https://developer.android.com/about/versions/16/features , https://developer.android.com/reference/android/Manifest.permission#POST_PROMOTED_NOTIFICATIONS

**Android 17 (API 37)** — status: "Android 17 has officially reached platform stability today with Beta 3. That means that the API surface is locked." (March 2026); the docs overview lists API 37 diffs plus QPR1 (updated 2026-04-22) and QPR2 Beta (updated 2026-08-28), so the stable release is out (**exact GA date not found on the fetched pages**). — https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html , https://developer.android.com/about/versions/17/overview
- Background audio hardening (all apps; stricter when targeting 37) — see 3.5/3.9. — https://developer.android.com/about/versions/17/behavior-changes-all#bg-audio
- "Android 17 introduces a new variant of `AlarmManager.setExactAndAllowWhileIdle` that accepts an `OnAlarmListener` instead of a `PendingIntent`. This new callback-based mechanism is ideal for apps that currently rely on continuous wakelocks" — listener alarms die with the process, so not for ringing. — https://developer.android.com/about/versions/17/features#allow-while-idle-alarms-listener
- `IntentSender.sendIntent()` now needs sender-side BAL opt-in. — https://developer.android.com/guide/components/activities/background-starts
- Other 17 changes (app memory limits, SMS OTP protection, `usesCleartextTraffic` deprecation, implicit URI grants, keystore limits, IME visibility after rotation) do not touch alarms. — https://developer.android.com/about/versions/17/behavior-changes-all , https://developer.android.com/about/versions/17/behavior-changes-17
- Nothing found in 17 changing `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `systemExempted`, or the `BOOT_COMPLETED` FGS rules.

---

## 4. Recommended permission set + manifest skeleton

### Permissions

| Permission | Why | Level / grant |
|---|---|---|
| `android.permission.USE_EXACT_ALARM` | Core alarm-clock function; exact alarms on API 33+ without a user toggle | normal, install-time; Play sensitive-permission declaration required |
| `android.permission.SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) | Exact alarms on Android 12/12L; pre-granted there, user-revocable | special app access |
| `android.permission.USE_FULL_SCREEN_INTENT` | Trigger screen over the lock screen | normal ≤33; special app access 34+, pre-granted via Play FSI declaration |
| `android.permission.POST_NOTIFICATIONS` | Alarm/FGS notification on 33+ | dangerous (runtime) |
| `android.permission.FOREGROUND_SERVICE` | Ringing service | normal |
| `android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | `systemExempted` type (exact-alarm holders) | normal; Play FGS-type declaration required |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedule after reboot / un-stop | normal |
| `android.permission.VIBRATE` | Haptics while ringing | normal |
| `android.permission.WAKE_LOCK` | Partial wake lock inside the ringing service | normal |

Not requested: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `SYSTEM_ALERT_WINDOW`, `TURN_SCREEN_ON`, `FOREGROUND_SERVICE_SPECIAL_USE`. Optional fallback if Play review rejects `systemExempted`: swap to `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `foregroundServiceType="mediaPlayback"`.

Play Console declarations to complete: (1) sensitive permission — exact alarm; (2) full-screen intent core functionality = alarm; (3) foreground service type(s) with description + video.

### Manifest skeleton (`androidApp/src/main/AndroidManifest.xml`)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Exact alarms -->
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"
        android:maxSdkVersion="32" />

    <!-- Trigger screen + notification -->
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Ringing service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Rescheduling -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application ...>

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Full-screen Trigger screen, launched only via the notification's full-screen PendingIntent -->
        <activity
            android:name=".alarm.TriggerActivity"
            android:exported="false"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:excludeFromRecents="true"
            android:launchMode="singleInstance"
            android:taskAffinity=""
            android:theme="@style/Theme.Snoozeloo.Trigger" />

        <!-- Fired by AlarmManager.setAlarmClock(); starts the ringing FGS synchronously -->
        <receiver
            android:name=".alarm.AlarmReceiver"
            android:exported="false" />

        <!-- Reschedules alarms after reboot, app update, time/zone change, exact-alarm grant -->
        <receiver
            android:name=".alarm.RescheduleReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
            </intent-filter>
        </receiver>

        <!-- Plays the tone / vibrates and owns the alarm notification with the full-screen intent -->
        <service
            android:name=".alarm.RingingService"
            android:exported="false"
            android:foregroundServiceType="systemExempted" />

    </application>
</manifest>
```

Notes on the skeleton:

- `android:exported="false"` on the `BOOT_COMPLETED` receiver: system-originated broadcasts reach non-exported receivers; the official sample omits the attribute, which is not allowed for filter-bearing components when targeting 31+. **Verify on device during implementation.**
- `AlarmReceiver.onReceive`: build the notification, `ContextCompat.startForegroundService(...)` immediately (do not go async first); the temporary allow-list is short. In `RingingService.onStartCommand` call `ServiceCompat.startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)` on API 34+, and without a type below.
- Notification: channel `IMPORTANCE_HIGH` with channel sound off; `setCategory(CATEGORY_ALARM)`, `setOngoing(true)`, `setFullScreenIntent(PendingIntent.getActivity(..., FLAG_IMMUTABLE), true)`, plus Dismiss/Snooze actions as `PendingIntent.getService`/`getBroadcast` (never trampolines to an activity).
- Scheduling: `AlarmManagerCompat.setAlarmClock(am, triggerMillis, showIntent, alarmIntent)` where `showIntent` opens the alarm's edit screen; `alarmIntent` is `PendingIntent.getBroadcast(..., FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)` with a per-alarm request code.
- Runtime checks, in order: `POST_NOTIFICATIONS` (33+) → `canScheduleExactAlarms()` (31–32 only; always true on 33+ with `USE_EXACT_ALARM`) → `canUseFullScreenIntent()` (34+) → `areNotificationsEnabled()` before each save. Each has a settings deep link (`ACTION_APP_NOTIFICATION_SETTINGS`, `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`).

---

## 5. Open points / unconfirmed

- Play review outcome for `foregroundServiceType="systemExempted"` from a third-party alarm app (docs enumerate the use case; Play's pre-set list does not). Fallback `mediaPlayback` is documented and policy-listed.
- Whether the full-screen `PendingIntent` needs any BAL opt-in on 15+/17 — no doc statement either way; System UI is the sender, so none is expected. Test with `StrictMode.detectBlockedBackgroundActivityLaunch()` on API 36/37.
- Behaviour of the `systemExempted` manifest bit on API 30–33 devices (unknown type bits) — test on an API 30 emulator.
- Exact Android 17 GA date (pages fetched show platform stability in March 2026 and QPR1/QPR2 betas through August 2026).

## 6. Sources (all fetched 2026-09-05)

- https://developer.android.com/develop/background-work/services/alarms/schedule
- https://developer.android.com/reference/android/app/AlarmManager
- https://developer.android.com/reference/android/app/AlarmManager.AlarmClockInfo
- https://developer.android.com/reference/android/Manifest.permission
- https://developer.android.com/reference/android/provider/Settings
- https://developer.android.com/reference/android/app/NotificationManager
- https://developer.android.com/reference/android/app/NotificationManager.Policy
- https://developer.android.com/reference/android/app/Notification
- https://developer.android.com/reference/android/app/Notification.Builder
- https://developer.android.com/reference/android/app/Activity
- https://developer.android.com/reference/android/R.attr
- https://developer.android.com/reference/android/app/KeyguardManager
- https://developer.android.com/reference/android/media/AudioAttributes
- https://developer.android.com/reference/android/os/VibrationAttributes
- https://developer.android.com/reference/android/os/Vibrator
- https://developer.android.com/about/versions/12/behavior-changes-12
- https://developer.android.com/about/versions/12/foreground-services
- https://developer.android.com/about/versions/13/behavior-changes-13
- https://developer.android.com/about/versions/13/features
- https://developer.android.com/about/versions/14/behavior-changes-14
- https://developer.android.com/about/versions/14/behavior-changes-all
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://developer.android.com/about/versions/15/behavior-changes-15
- https://developer.android.com/about/versions/15/behavior-changes-all
- https://developer.android.com/about/versions/15/changes/foreground-service-types
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/about/versions/16/features
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/about/versions/17/summary
- https://developer.android.com/about/versions/17/overview
- https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/launch
- https://developer.android.com/develop/background-work/services/fgs/declare
- https://developer.android.com/develop/background-work/services/fgs/timeout
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://developer.android.com/develop/ui/views/notifications/notification-permission
- https://developer.android.com/develop/ui/views/notifications/time-sensitive
- https://developer.android.com/develop/ui/views/notifications/build-notification
- https://developer.android.com/develop/ui/views/notifications/channels
- https://developer.android.com/guide/components/activities/background-starts
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://developer.android.com/topic/performance/appstandby
- https://developer.android.com/topic/performance/power/power-details
- https://developer.android.com/media/optimize/audio-focus
- https://support.google.com/googleplay/android-developer/answer/9888170 (Permissions and APIs that Access Sensitive Information)
- https://support.google.com/googleplay/android-developer/answer/16909972 (preview of the above)
- https://support.google.com/googleplay/android-developer/answer/13392821 (Foreground service and full-screen intent requirements)
- https://support.google.com/googleplay/android-developer/answer/16965181 (preview of the above)
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java
