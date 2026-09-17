# Lock Task (Kiosk) Mode

## What a Device Owner session configures

Applied by `LockdownPolicyEnforcer.apply()` when a session starts and removed by `release()` when it ends.

| Setting | API | Purpose |
|---|---|---|
| Lock task allowlist | `setLockTaskPackages` | Lockdown App + default/system dialer + allowed apps only |
| Lock task features | `setLockTaskFeatures` | `GLOBAL_ACTIONS` (power menu), `KEYGUARD` (secure lock screen), `HOME`; `SYSTEM_INFO` in Standard mode only. `NOTIFICATIONS` and `OVERVIEW` are never enabled |
| HOME override | `addPersistentPreferredActivity` + enabling the `LockdownHomeAlias` activity-alias | Home, and the first screen after boot, is the lockdown screen |
| Uninstall block | `setUninstallBlocked` | Lockdown App cannot be uninstalled |
| User restrictions | `addUserRestriction` | See below |
| Automatic time | `setAutoTimeEnabled` (API 30+) / `setAutoTimeRequired` | Network time stays on; restored afterwards if Lockdown App turned it on |

User restrictions:

| Standard | Strict adds |
|---|---|
| `no_safe_boot` | `no_install_apps` |
| `no_control_apps` (force stop / clear data / disable) | `no_add_user` |
| `no_uninstall_apps` | `no_user_switch` |
| `no_config_date_time` | `no_debugging_features` (release builds only) |

Only restrictions that were **not already set** are added, and only those are removed later
(tracked in `lockdown_state.applied_restrictions`). Restrictions set by someone else are left alone.

Never restricted: factory reset, outgoing calls, emergency functions, recovery.

## Entering and leaving lock task mode

- `LockdownActivity` (`lockTaskMode="if_whitelisted"`, own task, excluded from recents) calls
  `startLockTask()` whenever a session is active and lock task is not.
- With Device Owner the app is permitted, so there is no prompt.
- Without Device Owner, Android shows the **screen pinning** prompt (once per screen instance).
  The user can unpin; exit attempts are counted and a session notification leads back.
- At the end, the engine empties the allowlist (the system then exits lock task), disables the
  HOME alias and clears the persistent preferred activity. The screen calls `stopLockTask()` and
  finishes; if it was running as HOME it hands over to the real launcher.

## Allowed apps

- The picker lists launchable apps via a `<queries>` launcher intent (no `QUERY_ALL_PACKAGES`).
- The default dialer is always included and shown as *Always available*.
- The lockdown screen only launches packages from the persisted allowlist; uninstalled packages are
  dropped before the allowlist is applied.
- Allowed apps run in their own tasks inside lock task mode; Home brings the user back to the
  lockdown screen.

## Verified on an API 35 emulator (Device Owner)

| Action during a session | Observed result |
|---|---|
| Home key | Lockdown screen (via HOME alias) |
| Recents key | No effect |
| Back key | No effect (counted as an exit attempt) |
| `am start` Settings | Refused by the system |
| `pm uninstall` | `DELETE_FAILED_APP_PINNED` |
| `am force-stop` | Ignored; process and lock task unchanged |
| Reboot mid-session | Lockdown screen opened by the system as HOME; session restored; ended on time |
| Session end | Restrictions removed, lock task exited, Home returns to the system launcher |
