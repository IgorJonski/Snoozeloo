---
status: accepted
---

# iOS alarms are delivered through AlarmKit, so the app targets iOS 26+

Snoozeloo must ring reliably when the app is killed, with a full-screen Trigger and a Snooze that works from the lock screen. Local notifications cannot do that (30 s sound cap, no full-screen wake, no real alarm semantics), so we build on AlarmKit and set the iOS deployment target to 26.0, accepting the reduced device reach. Android stays at minSdk 30.

Accepted on 2026-09-06 after [AlarmKit capabilities and constraints (#2)](https://github.com/IgorJonski/Snoozeloo/issues/2) and [iOS alarm delivery (#12)](https://github.com/IgorJonski/Snoozeloo/issues/12): weekday repeats, one-shots, a five-minute snooze, a bundled sound and app-side Turn Off/Snooze hooks are all achievable, and no other iOS mechanism gives an alarm that breaks Silent and Focus. The delivery design is ADR-0007. Degradations accepted on iOS, none of which has a fallback that notifications could improve:

- **Volume and Vibrate** have no effect on an AlarmKit alert (system "Ringtone and Alerts" level, system haptics); both rows are hidden in Alarm Settings on iOS.
- **Silent** is a bundled near-silent sound file, because AlarmKit always plays something.
- **The Trigger is the system alert** (title, Stop, Snooze); the Compose Trigger appears only as an overlay when the app is already in the foreground.
- **No five-minute ringing timeout**: the alert duration belongs to the system.
- **Extra native code**: one Swift bridge class, two `LiveActivityIntent`s and a widget-extension target for the snooze countdown, all within ADR-0003's "Swift only as a bridge".
- **Hardware verification** is mandatory; the simulator does not alert reliably.
