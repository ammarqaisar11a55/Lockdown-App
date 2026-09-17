# Scheduling

## Model

Schedules store **wall-clock** times plus a repeat rule, not absolute timestamps:

| Rule | Example |
|---|---|
| `Once(date)` | Today 09:00 → 13:00 |
| `Daily` | Every day 08:00 → 12:00 |
| `Weekly(days)` | Mon–Fri 08:00 → 17:00 (Weekdays preset or custom days) |

An end time at or before the start means the session ends the next day (overnight).
A day may hold any number of non-overlapping sessions.

`ScheduleCalculator` resolves a schedule into concrete `SessionWindow`s
(`start`, `end` instants) for the device's **current** timezone. An occurrence is identified by
`occurrenceKey = "<scheduleId>@<startEpochMillis>"`.

## Validation and overlap policy

`ScheduleValidator` rejects: blank or long names, equal start/end, weekly rules without days,
one-time sessions that already started, and overlaps with another **enabled** schedule.

`ScheduleOverlapChecker` compares wall-clock occurrences (in UTC, so daylight saving cannot create
false results) over one reference week for recurring rules, or around the date of one-time rules.
The day before each reference date is included so overnight sessions are compared correctly.

Runtime merge policy (for overlaps that appear later, e.g. after a timezone change, or an ad-hoc
session running into a schedule):

- A running session is never shortened or replaced.
- When it ends, any schedule window still open starts immediately for its remaining time.
- Among simultaneous candidates, the window that ends last wins.

## Time handling

The engine persists `start`, `end`, `zoneId`, `scheduleId` and a **monotonic checkpoint**
(`bootCount`, `elapsedRealtime`, remaining ms) for the active session. State is always derived
from timestamps, never from a countdown timer.

| Situation | Behaviour |
|---|---|
| Reboot | `BOOT_COMPLETED` (and the HOME activity starting the process) → reconcile; an active session is restored, an expired one completed; the next alarm is re-armed |
| Process death | `Application.onCreate` → reconcile (`PROCESS_START`) |
| App update | `MY_PACKAGE_REPLACED` → reconcile |
| Timezone change | `TIMEZONE_CHANGED` → reconcile; recurring schedules follow local wall-clock time in the new zone; a running session keeps its absolute end |
| Daylight saving | Start inside a spring-forward gap moves forward by the gap; ambiguous autumn times use the earlier offset; a window collapsed to zero length is skipped |
| Clock moved forward | Within the same boot a session ends only when **both** the wall clock and the monotonic clock say it has ended (`SessionClock`) |
| Clock moved backward | The session may last longer (never shorter); Device Owner sessions block manual clock changes anyway |
| Missed alarm / device off at start | On the next reconcile an open window starts immediately for its remaining time; a window that fully passed is ignored |
| Device sleep / Doze | `setExactAndAllowWhileIdle` fires in Doze |
| Exact alarms denied | `setAndAllowWhileIdle` fallback; the diagnostics screen shows a warning and a fix button; `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` triggers rescheduling |

## Alarms

Only **one** alarm exists at any time: `LockdownDecider.nextWakeUp()`, the earliest of

- the active session's end (monotonic-aware),
- the next session start,
- its countdown start (auto-start sessions only),
- its reminder time,
- the end of a pending manual-start window.

The receiver is not exported and the `PendingIntent` is explicit and `FLAG_IMMUTABLE`.

## Countdown, reminders, manual start

- **Reminder** (off/5/15/30 min): one notification per occurrence ("Focus session begins in 15 minutes.").
- **Countdown** (off/1/5/10 min): the dashboard shows a live countdown with *Cancel before session
  starts*; cancelling adds the occurrence to `skippedOccurrences`. Once the session is active,
  cancellation is refused by the engine.
- Any upcoming occurrence can be skipped from the dashboard before it starts.
- **Start automatically = off**: at the scheduled time a notification offers *Start session*;
  nothing is enforced until the user starts it, and it still ends at the scheduled end.

## Editing rules

- The schedule being enforced cannot be edited, disabled or deleted (the UI is unreachable during
  lockdown anyway; the use cases refuse it as well).
- Deleting or disabling a schedule never ends a session that already started.
