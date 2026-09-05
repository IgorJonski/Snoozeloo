---
status: proposed
---

# iOS alarms are delivered through AlarmKit, so the app targets iOS 26+

Snoozeloo must ring reliably when the app is killed, with a full-screen Trigger and a Snooze that works from the lock screen. Local notifications cannot do that (30 s sound cap, no full-screen wake, no real alarm semantics), so we build on AlarmKit and set the iOS deployment target to 26.0, accepting the reduced device reach. Android stays at minSdk 30.

Status is `proposed` until the "AlarmKit capabilities and constraints" research ticket confirms custom sounds, 5-minute snooze, weekday repeats and per-alarm volume/vibration are achievable; if any of those is impossible the fallback is AlarmKit plus a degraded in-app path, never notifications alone.
