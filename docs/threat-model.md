# Threat Model

Attacker: the device's own user, trying to leave an active focus session voluntarily, with
physical access and normal user knowledge. Out of scope: a user with ADB access to a **debug**
build, bootloader/root access, and hardware attacks.

Levels: **G** = guaranteed by Android API · **S** = supported · **D** = device/manufacturer
dependent · **N** = not possible for normal third-party apps.
"DO" = Device Owner session; "no DO" = screen-pinning fallback.

---

### T1 — User presses Home
- **Android capability:** lock task features (`LOCK_TASK_FEATURE_HOME`) + persistent preferred HOME activity (G/S).
- **Mitigation:** HOME resolves to `LockdownHomeAlias` → lockdown screen.
- **Remaining limitation:** no DO: Home works only after the user unpins (pinning can be exited).
- **Test:** start a session; `adb shell input keyevent KEYCODE_HOME`; `dumpsys activity activities` shows the lockdown screen on top and `mLockTaskModeState=LOCKED`.

### T2 — User presses Recents
- **Android capability:** `LOCK_TASK_FEATURE_OVERVIEW` not granted (G).
- **Mitigation:** Overview is unavailable; the lockdown task is excluded from recents.
- **Remaining limitation:** no DO: holding Back + Overview unpins.
- **Test:** `adb shell input keyevent KEYCODE_APP_SWITCH`; the top activity is unchanged.

### T3 — User launches another application
- **Android capability:** lock task allowlist (G).
- **Mitigation:** only Lockdown App, the dialer and allowed apps can start; the lockdown screen launches only persisted allowed packages.
- **Remaining limitation:** allowed apps may themselves open other content (e.g. a browser inside an allowed app). Choose allowed apps carefully.
- **Test:** `adb shell am start -a android.settings.SETTINGS` → "Activity not started"; tap an allowed app on the lockdown screen → it opens.

### T4 — User attempts to uninstall the application
- **Android capability:** Device Owner apps cannot be uninstalled; `setUninstallBlocked`; `no_uninstall_apps` (G).
- **Mitigation:** all three during DO sessions.
- **Remaining limitation:** no DO: the app can be uninstalled after unpinning; factory reset removes it (N).
- **Test:** `adb shell pm uninstall <package>` → `DELETE_FAILED_*`.

### T5 — User opens Settings
- **Android capability:** allowlist (G); `no_control_apps`, `no_safe_boot`, `no_config_date_time` (G).
- **Mitigation:** Settings is not in the allowlist; restrictions apply even if some Settings surface becomes reachable.
- **Remaining limitation:** if the user allowlists Settings, restrictions still apply but other settings become reachable. Quick Settings are hidden because notifications are disabled in lock task.
- **Test:** T3 procedure; `adb shell dumpsys user` lists the restrictions.

### T6 — User reboots the phone
- **Android capability:** user restrictions and the persistent preferred activity persist across reboot (G); `BOOT_COMPLETED` (S, delivery timing D); `no_safe_boot` (G).
- **Mitigation:** the system opens the lockdown screen as HOME → process start → reconcile restores the session and lock task; `BOOT_COMPLETED` reconciles again and re-arms alarms.
- **Remaining limitation:** a session that ends while the phone is off is completed on the next boot. Recovery mode and factory reset remain available (N).
- **Test:** start a session; `adb reboot`; after boot `mLockTaskModeState=LOCKED` and the log shows "Session recovered". Verified on API 35.

### T7 — App process dies
- **Android capability:** process restart through HOME, alarms or receivers (S).
- **Mitigation:** all state in Room; `Application.onCreate` reconciles (`PROCESS_START`); if lock task is no longer active the lockdown screen is relaunched; drift repair.
- **Remaining limitation:** whether the system keeps the lock task alive across a process kill was not verified on device (D); the relaunch covers it. Without DO, the process may stay dead until the next alarm or app open.
- **Test:** unit test `process death restores the active session`; on device, `adb shell am kill` while in the background, then reopen.

### T8 — Device loses Internet
- **Android capability:** n/a.
- **Mitigation:** no network use at all; no `INTERNET` permission.
- **Remaining limitation:** none.
- **Test:** enable airplane mode; schedules, lockdown and history behave identically.

### T9 — User changes the time
- **Android capability:** `no_config_date_time` + automatic time (G, DO only); monotonic clock (G).
- **Mitigation:** manual changes are blocked during DO sessions. Within a boot, a session ends only when the monotonic clock also confirms it (`SessionClock`). Time/zone broadcasts trigger reconciliation.
- **Remaining limitation:** no DO: moving the clock forward and then rebooting ends the session early (the monotonic anchor resets on reboot). Moving the clock backward only lengthens a session.
- **Test:** unit tests `moving the clock forward does not end the session early`, `time change mid-session keeps it locked`.

### T10 — User disables permissions
- **Android capability:** runtime/app-op permissions are user-controlled (S).
- **Mitigation:** enforcement does not depend on notifications. If exact alarms are revoked, the app falls back to inexact alarms and reschedules on the permission-change broadcast; the lockdown screen ends sessions on time while visible. Diagnostics show the state.
- **Remaining limitation:** without exact alarms a start may be late by about a minute or more (D); Settings is unreachable during DO sessions, so revocation can only happen between sessions.
- **Test:** `adb shell appops set <package> SCHEDULE_EXACT_ALARM deny`; `dumpsys alarm` shows a windowed alarm; allow again → exact.

### T11 — User escapes through notifications
- **Android capability:** `LOCK_TASK_FEATURE_NOTIFICATIONS` not granted (G).
- **Mitigation:** the notification shade and Quick Settings are unavailable in DO sessions; session notifications are minimal and only open Lockdown App.
- **Remaining limitation:** no DO: the shade is reachable after unpinning.
- **Test:** swipe down during a session → nothing expands.

### T12 — User changes the launcher
- **Android capability:** `addPersistentPreferredActivity` for HOME (G for DO).
- **Mitigation:** the default-launcher choice is overridden during sessions; Settings is unreachable anyway.
- **Remaining limitation:** none with DO.
- **Test:** `adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME` resolves to `LockdownHomeAlias` during a session and to the normal launcher afterwards.

### T13 — User tries to disable device management
- **Android capability:** a Device Owner cannot be deactivated from Settings (G); `onDisableRequested` warns device admins.
- **Mitigation:** in-app removal is refused while a session is active; Settings is unreachable anyway.
- **Remaining limitation:** factory reset (N). ADB removal only works for test-only builds.
- **Test:** during a session the Settings screen is unreachable; afterwards *Remove device management* works.

### T14 — Battery optimization kills the application
- **Android capability:** Doze-exempt exact alarms (S); OEM task killers (D).
- **Mitigation:** no long-running service to kill; the lockdown screen is the HOME activity and lock task owner (never a "background" app during a session); every wake-up re-reconciles; diagnostics link to battery settings and warn on known aggressive OEMs.
- **Remaining limitation:** some OEMs delay alarms or receivers for unrestricted apps; the start may be late on those devices (D).
- **Test:** `adb shell dumpsys deviceidle force-idle`; confirm the alarm still fires (`dumpsys alarm`, log "Reconcile trigger=ALARM").
