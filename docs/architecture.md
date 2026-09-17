# Architecture

## Layers

```
UI              Compose screens (feature/*), stateless where possible
 ↓
ViewModel       StateFlow UI state, collectAsStateWithLifecycle
 ↓
Use cases       domain/usecase — SaveSchedule, DeleteSchedule, SetScheduleEnabled,
                ResetLocalData, FocusStatsCalculator, AppModeResolver
 ↓
Core logic      core/scheduling  ScheduleCalculator, ScheduleOverlapChecker, ScheduleValidator
                core/security    LockdownDecider, SessionClock, PolicyReconciliationEngine
 ↓
Data / Device   data/*           Room (schedules, allowed apps, lockdown state, history), DataStore (settings)
                core/device      DevicePolicyController, LockdownPolicyEnforcer, DeviceCapabilityChecker
                core/scheduling  TransitionScheduler (AlarmManager)
```

Rules enforced by the structure:

- `DevicePolicyManager` is only touched in `AndroidDevicePolicyController`. Composables never call it.
- Scheduling never lives in Activities; it is `LockdownDecider` + `TransitionScheduler`.
- Business rules (`ScheduleCalculator`, `LockdownDecider`, `SessionClock`, `FocusStatsCalculator`)
  have no Android dependencies and are unit-tested on the JVM.
- The only Activity-bound calls — `startLockTask()` / `stopLockTask()` — live in `LockdownActivity`.

## Application modes

`AppMode` (shown on the dashboard) is derived, never stored:

```
UNPROVISIONED → NORMAL → SCHEDULED → COUNTDOWN → LOCKDOWN → COMPLETED
```

The persisted engine state is smaller (`LockdownPhase`): `IDLE`, `COUNTDOWN`, `ACTIVE`.
Recovery paths are triggers rather than states:

```
LOCKDOWN → DEVICE_REBOOT  → BOOT / PROCESS_START trigger → reconcile → lockdown restored
LOCKDOWN → PROCESS_DEATH  → PROCESS_START trigger        → reconcile → state restored
```

## Persisted state

| Store | Contents |
|---|---|
| `focus_schedules` | Wall-clock start/end minute, repeat type, once date, weekday mask, strict, enabled, auto-start |
| `allowed_applications` | Package name, label |
| `lockdown_state` (single row) | Phase, current session window, zone, history id, enforcement level, monotonic checkpoint, restrictions this app added, skipped/started/reminded occurrence keys |
| `session_history` | Scheduled start, actual start, expected end, actual end, status, strict, enforcement, recovery count, exit attempts |
| DataStore `settings` | Onboarding flag, countdown minutes, reminder minutes, daily goal, message |

Room schemas are exported to `app/schemas`. Destructive migration is never enabled; every
schema change must ship a `Migration`, because wiping `lockdown_state` would silently end a session.

## Policy Reconciliation Engine

`PolicyReconciliationEngine.reconcile(trigger)`:

1. Read settings, schedules and persisted state; read the wall clock, zone, `elapsedRealtime`
   and boot count.
2. Prune stale occurrence bookkeeping; re-anchor the monotonic checkpoint after a reboot.
3. `LockdownDecider.decide()` → `Active(continuing?)`, `Countdown`, `AwaitingStart` or `Idle`.
4. Apply the difference:
   - **Start:** create a history row → **persist ACTIVE** → apply device policy → persist the
     restrictions actually added → notify → launch the lockdown screen.
   - **Continue:** verify device policy (`needsRepair`) and re-apply on drift; count recoveries;
     relaunch the lockdown screen if lock task is not active.
   - **End:** release policy (only restrictions this app added) → finish history → persist IDLE → notify.
5. When not active, release any leftover session policy (crash recovery).
6. Mark orphaned `IN_PROGRESS` history rows as `INTERRUPTED`.
7. Update countdown, manual-start and reminder notifications.
8. Save state and set **one** exact alarm for `LockdownDecider.nextWakeUp()`.

A `Mutex` serializes all runs; every run is idempotent.

Triggers: `BOOT`, `PROCESS_START`, `PACKAGE_REPLACED`, `ALARM`, `TIME_CHANGED`,
`PERMISSION_CHANGED`, `APP_FOREGROUND`, `SESSION_TIMER`, `SCHEDULES_CHANGED`, `SETTINGS_CHANGED`,
`USER_ACTION`.

## Background execution

No foreground service is used. It is not needed:

- Timing uses `AlarmManager.setExactAndAllowWhileIdle` (inexact fallback when not permitted).
- During a Device Owner session the lockdown screen is the persistent HOME activity and the lock
  task owner, so the system itself brings it back.
- The countdown on screen is computed from timestamps once per second **only while visible**; no
  background polling exists.

## Build variants

| Variant | Differences |
|---|---|
| `debug` | `.debug` application id suffix, verbose logging with in-memory buffer, Developer tools screen (`src/debug`), ADB stays enabled during Strict sessions |
| `release` | R8 minify + resource shrinking, `Log.d/v` stripped, no developer code (`src/release` provides an empty stand-in), Strict sessions disable debugging features |

## Optional backend (not implemented)

The app is intentionally offline. If cloud sync is ever added it must sit beside, never inside,
enforcement:

```
Android ── Local database ── Device Policy Engine
   └── Optional sync API (e.g. Node.js/Express + PostgreSQL) — schedules/history backup only
```

The engine must keep reading only local state so a network outage can never unlock a device.
