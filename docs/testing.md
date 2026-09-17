# Testing

## Automated tests

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:lintDebug
```

```bash
./gradlew :app:connectedDebugAndroidTest
```

### Unit tests (JVM) — `app/src/test`

| Suite | Covers |
|---|---|
| `ScheduleCalculatorTest` | Daily/weekly/one-time windows, inclusive start/exclusive end, overnight sessions, disabled schedules, ordering across date boundaries, timezone change, daylight-saving gap/overlap |
| `ScheduleOverlapCheckerTest` | Overlaps, adjacency, multiple sessions per day, weekday separation, overnight and week-wrap overlaps, one-time vs recurring |
| `ScheduleValidatorTest` | Name, zero length, empty days, past one-time sessions, overlap message |
| `SessionClockTest` | Remaining time from timestamps, expiry, forward clock change, reboot, unknown boot count |
| `LockdownDeciderTest` | State transitions, missed alarms, schedule deletion during a session, back-to-back sessions, countdown → lockdown, skipping, manual start, wake-up calculation, reminders, one-time completion |
| `PolicyReconciliationEngineTest` | Engine against fakes: persist-before-apply, release of owned restrictions only, process death, reboot during/after a session, policy drift repair, leftover cleanup, time change, screen-pinning fallback and upgrade, countdown cancel/refusal, focus now, ad-hoc + scheduled merge, manual start, reminders, exit attempts, orphaned history, bookkeeping pruning |
| `FocusStatsCalculatorTest` | Today/week totals, running sessions, midnight split, ad-hoc planning |
| `AppModeResolverTest` | Mode priority and completed-state expiry |
| `RestrictionPolicyTest` | Restriction sets, debug vs release, emergency/factory-reset never restricted, notifications/overview never enabled, allowlist composition |
| `MappersTest` | Day masks, schedule and state round-trips, corrupt-row handling |
| `DurationFormatterTest` | Clock, short and spoken formats |

### Instrumentation tests — `app/src/androidTest`

| Suite | Covers |
|---|---|
| `FocusDatabaseTest` | Room DAOs: schedules, single-row state, history lifecycle and orphans, atomic allowlist replacement |
| `DevicePolicyControllerTest` | Device Owner detection; safe degradation without DO; **with DO:** apply and release the full lockdown policy, and drift detection (skipped unless provisioned) |
| `OnboardingScreenTest` | Page flow, Learn more, setup vs skip |
| `DashboardScreenTest` | Progress and upcoming sessions, countdown cancel confirmation, unprovisioned setup link |
| `ScheduleEditorScreenTest` | Create → review → confirm, cancelling the review, Strict mode confirmation, repeat options, edit mode with delete confirmation and errors |
| `LockdownScreenTest` | Session info, no unlock control, TalkBack summary, allowed-app launch, pinning disclosure |

### Running Device Owner tests

1. Use an emulator without accounts (a new AVD works).
2. Install the app and provision it:

   ```bash
   ./gradlew :app:installDebug
   ```

   ```bash
   adb shell dpm set-device-owner com.example.focuslock.debug/com.example.focuslock.core.device.FocusDeviceAdminReceiver
   ```

3. Run the instrumentation tests. With little RAM, build first and run with `am instrument`:

   ```bash
   ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
   ```

   ```bash
   adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   ```

   ```bash
   adb shell am instrument -w com.example.focuslock.debug.test/androidx.test.runner.AndroidJUnitRunner
   ```

## Manual scenarios

Use a Device Owner test device and the debug **Developer tools** screen (Settings ›
Developer tools). Follow logs with `adb logcat -s LOCKDOWN_ENGINE LOCKDOWN_UI DEVICE_POLICY`.

| # | Scenario | Steps | Expected |
|---|---|---|---|
| 1 | Start lockdown normally | *Create schedule starting in ~2 min*, press Home, wait | Countdown notification, then the lockdown screen at the start minute; `mLockTaskModeState=LOCKED` |
| 2 | Kill the process | During a session: `adb shell am kill com.example.focuslock.debug` (or `am force-stop`) | Session remains; screen restored |
| 3 | Reopen the app | Launch FocusLock from adb | Redirected to the lockdown screen |
| 4 | Reboot | `adb reboot` during a session | Lockdown screen after boot; the session ends on time |
| 5 | Change timezone | `adb shell cmd alarm set-timezone Asia/Tokyo` | Running session unchanged; upcoming sessions shown at local wall-clock time |
| 6 | Disable Internet | Airplane mode | No difference |
| 7 | Miss an alarm | Power off before a start, boot mid-window | Locks immediately for the remaining time |
| 8 | Overlapping schedule | Create 08:00–12:00, then 11:00–13:00 | "Overlaps with …" error |
| 9 | Delete a schedule | Delete an upcoming schedule | Alarm moves to the next session |
| 10 | Disable a schedule | Toggle off | Not started; re-enabling checks overlaps |
| 11 | Navigation during lockdown | Home / Recents / Back / swipe down | Stays on the lockdown screen |
| 12 | App launch during lockdown | `adb shell am start -a android.settings.SETTINGS` | Refused; allowed apps open from the lockdown screen |
| 13 | Uninstall as Device Owner | `adb shell pm uninstall com.example.focuslock.debug` | `DELETE_FAILED_*` |
| 14 | Settings access | Try to open Settings | Not reachable; `dumpsys user` shows the restrictions |

### Results recorded on 2026-09-17 (API 35 Google APIs x86_64 emulator, Device Owner)

- Unit tests: 99 passed. Instrumentation: 21 passed without DO; Device Owner suite 4/4 with DO
  (the non-DO case skipped).
- Scenarios 1, 4, 11, 12, 13 and 14 passed as described in [kiosk-mode.md](kiosk-mode.md).
  `am force-stop` during a session was ignored by the system.
- Scenario 1 initially exposed a bug (a countdown never became a lockdown); it was fixed and is
  covered by `countdown turns into lockdown at the start time` and
  `countdown is followed by lockdown when not cancelled`.
- Not yet run on physical devices or OEM builds; scenarios 2 (`am kill`), 5, 6, 7 and 9–10 were
  covered by unit tests only.
