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
An Alarm with no Repeat Days; it rings once, at the next Occurrence of its Alarm Time.
_Avoid_: Single alarm, non-repeating alarm

**Occurrence**:
A concrete date-time at which an Alarm rings. The **Next Occurrence** is the soonest future one, shown as a countdown ("1d 4h 45min").
_Avoid_: Instance, firing, trigger

**Trigger**:
The moment an Occurrence arrives and the Alarm starts ringing; also the full-screen Alarm Trigger screen shown then.
_Avoid_: Fire, go off, ring (as a noun)

**Turn Off**:
Stopping a ringing Alarm from the Trigger screen; ends this Occurrence.
_Avoid_: Dismiss, stop, cancel

**Snooze**:
Stopping a ringing Alarm and scheduling a one-time Occurrence 5 minutes later with the same settings, without changing the Alarm's Repeat Days schedule.
_Avoid_: Postpone, delay, remind later

**Enabled / Disabled**:
Whether an Alarm is scheduled to ring. Toggled from the alarm card. A Disabled Alarm keeps all its settings.
_Avoid_: Active/inactive, on/off (in code), armed

**Ringtone**:
The sound an Alarm plays when it Triggers. **Silent** is a Ringtone that plays no sound.
_Avoid_: Sound, tone, alarm sound, melody

**Volume**:
The loudness of the Ringtone when the Alarm Triggers, 0–100%, default 50%.
_Avoid_: Level, loudness

**Bedtime Hint**:
The text "Go to bed at XX:YY to get 8h of sleep" on an alarm card, computed as Alarm Time minus 8 hours.
_Avoid_: Sleep reminder, bedtime suggestion
