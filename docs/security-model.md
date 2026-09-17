# Security Model

## Goal

> "You cannot voluntarily escape the focus session."

Not: "the application controls every aspect of Android." FocusLock uses the strongest
**legitimate** controls Android offers and documents every remaining escape path.

## Principles

1. **Local enforcement first.** Enforcement depends only on on-device state; there is no network
   permission and no backend.
2. **Official APIs only.** DevicePolicyManager, lock task mode, UserManager restrictions,
   AlarmManager. No root, hidden APIs, accessibility abuse, overlays or exploits.
3. **Never block safety.** Emergency calls, power menu, secure lock screen, recovery mode and
   factory reset stay available (`RestrictionPolicyTest` asserts this).
4. **Fail safe and reconcile.** Persist before acting; re-verify the device against the persisted
   state on every trigger; repair drift; clean up leftovers.
5. **Least privilege.** Three normal permissions, each justified (below).
6. **No silent privilege.** Device Owner is provisioned only by the user or administrator.

## Capability levels

| Level | Meaning | Examples |
|---|---|---|
| GUARANTEED BY ANDROID API | Enforced by the platform once configured | Lock task allowlist; blocked uninstall of a Device Owner; `no_safe_boot`, `no_control_apps`, `no_config_date_time` restrictions; Device Owner cannot be removed from Settings |
| SUPPORTED | Public API, with caveats | Persistent HOME activity; exact alarms (user-grantable); boot receiver; screen pinning fallback |
| DEVICE/MANUFACTURER DEPENDENT | Varies by OEM/version | Background execution limits; power-menu contents; emergency dialer entry points; boot receiver delivery timing |
| NOT POSSIBLE FOR NORMAL THIRD-PARTY APPS | Not attempted | Blocking recovery mode/bootloader; preventing factory reset from recovery; blocking emergency calls; surviving a factory reset; preventing power-off |

## Trust boundaries

| Component | Exposure | Protection |
|---|---|---|
| `FocusDeviceAdminReceiver` | Exported | `BIND_DEVICE_ADMIN` permission (system only) |
| `ProvisioningModeActivity`, `PolicyComplianceActivity` | Exported | `BIND_DEVICE_ADMIN` permission |
| `BootReceiver`, `TimeChangeReceiver` | Exported (system broadcasts) | Actions validated; handled actions are protected broadcasts; reconciliation is idempotent, so spoofing cannot change the outcome |
| `LockdownHomeAlias` | Exported (HOME), disabled outside sessions | Opens the lockdown screen only; accepts no extras |
| `TransitionAlarmReceiver`, `NotificationActionReceiver` | Not exported | Explicit `FLAG_IMMUTABLE` PendingIntents; the occurrence key is length- and pattern-checked, and the engine verifies it is a real pending occurrence |
| `LockdownActivity` | Not exported | Launches only packages in the persisted allowlist |
| `MainActivity` | Launcher | Redirects to the lockdown screen while a session is active |

No action can **end** a session except reconciliation observing that the session has expired.
There is no unlock button, no debug bypass in release builds, and no IPC entry point that shortens
a session.

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_BOOT_COMPLETED` | Restore an active session and re-arm the alarm after reboot |
| `POST_NOTIFICATIONS` | Reminders, countdown, completion (runtime-requested; optional) |
| `SCHEDULE_EXACT_ALARM` | Start and end sessions on time (user-grantable; inexact fallback) |

Not requested: `INTERNET`, `QUERY_ALL_PACKAGES` (a `<queries>` launcher intent is used instead),
`PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, accessibility, `USE_EXACT_ALARM`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE`.

## Data handling

- All data is local: schedules, allowed app names, history and settings.
- `allowBackup="false"` and data-extraction rules exclude everything, so lockdown state is never
  restored onto another device.
- Nothing sensitive is stored; encryption at rest relies on Android file-based encryption.
- Logs never contain app lists or session names. Release builds strip `Log.d`/`Log.v` and log
  only warnings and errors; the in-memory log buffer exists only in debug builds.
- No WebView, no dynamic code loading, no local server, no secrets in the repository.

## Anti-bypass summary

See [threat-model.md](threat-model.md) for T1–T14. In short:

- **Navigation (Home, Recents, notifications):** lock task features plus the HOME override.
- **Other apps and Settings:** lock task allowlist.
- **Uninstall, disable, force stop, safe mode:** uninstall block plus user restrictions.
- **Reboot and process death:** persisted state, HOME override, boot receiver, reconciliation.
- **Clock manipulation:** `no_config_date_time`, automatic time, monotonic guard.
- **Admin removal:** Device Owner cannot be deactivated from Settings.
- **Remaining escape paths:** factory reset, recovery mode, and the absence of Device Owner.
