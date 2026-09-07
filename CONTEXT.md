# Snoozeloo

An alarm-clock app (Android + iOS, Compose Multiplatform). A user creates alarms that ring at a chosen time, optionally on repeating weekdays, with a chosen ringtone, volume and vibration.

## Language

**Alarm**:
A user-created reminder that rings at an Alarm Time. Has an optional Name, Repeat Days, a Ringtone, a Volume, a Vibrate flag, and is either Enabled or Disabled.
_Avoid_: Reminder, timer, event

**Alarm Time**:
The wall-clock hour and minute (00:00–23:59) an Alarm rings at, independent of date.
_Avoid_: Trigger time, schedule

**Repeat Days**:
The set of weekdays (Mo–Su) an Alarm rings on. An Alarm with no Repeat Days is a One-shot Alarm.
_Avoid_: Schedule, recurrence, weekdays

**One-shot Alarm**:
An Alarm with no Repeat Days; it rings once, at the next Occurrence of its Alarm Time (today if still ahead, otherwise tomorrow), and becomes Disabled after Turn Off.
_Avoid_: Single alarm, non-repeating alarm

**Occurrence**:
A concrete date-time at which an Alarm rings, either a **Regular Occurrence** (from Alarm Time and Repeat Days) or a Snoozed Occurrence. An Alarm has at most one pending Occurrence of each kind. The **Next Occurrence** is the soonest future one, shown as a countdown ("1d 4h 45min").
_Avoid_: Instance, firing, trigger

**Snoozed Occurrence**:
The one-time Occurrence created by a Snooze, exactly five minutes after the Snooze. It survives changes to the Alarm's Name, Ringtone, Volume and Vibrate, and is dropped when Alarm Time, Repeat Days or Enabled change or the Alarm is deleted.
_Avoid_: Snooze alarm, pending snooze, countdown

**Trigger**:
The moment an Occurrence arrives and the Alarm starts ringing; also the surface shown then, on which the user can Turn Off or Snooze (the app's full-screen Alarm Trigger screen, or the system's own alarm alert where the platform owns it).
_Avoid_: Fire, go off, ring (as a noun)

**Turn Off**:
Stopping a ringing Alarm from the Trigger screen; ends this Occurrence. Five minutes of unanswered ringing, or a newer Occurrence taking over the Trigger, count as Turn Off.
_Avoid_: Dismiss, stop, cancel, missed

**Snooze**:
Stopping a ringing Alarm and scheduling a Snoozed Occurrence 5 minutes later with the same settings, without changing the Alarm's Repeat Days schedule. Unlimited per Occurrence.
_Avoid_: Postpone, delay, remind later

**Enabled / Disabled**:
Whether an Alarm is scheduled to ring. Toggled from the alarm card. A Disabled Alarm keeps all its settings.
_Avoid_: Active/inactive, on/off (in code), armed

**Ringtone**:
The sound an Alarm plays when it Triggers, chosen from the platform's catalog. **Silent** is a Ringtone that plays no sound.
_Avoid_: Sound, tone, alarm sound, melody

**Default Ringtone**:
The Ringtone that stands for the platform's current default alarm sound, whatever it is at the moment the Alarm Triggers. Every new Alarm starts with it.
_Avoid_: System ringtone, fallback, standard sound

**Preview**:
A short, one-time playing of a Ringtone from the Ringtone Setting screen so the user can hear it before choosing. Only one Preview plays at a time; it never affects an Alarm.
_Avoid_: Sample, demo, test play

**Volume**:
The loudness of the Ringtone when the Alarm Triggers, 0–100%, default 50%.
_Avoid_: Level, loudness

**Bedtime Hint**:
The text "Go to bed at XX:YY to get 8h of sleep" on an alarm card, computed as Alarm Time minus 8 hours.
_Avoid_: Sleep reminder, bedtime suggestion
