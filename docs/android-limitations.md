# Android Limitations

What Android does and does not let Lockdown App do. Levels:

- **GUARANTEED BY ANDROID API** — enforced by the platform once configured.
- **SUPPORTED** — public API with caveats.
- **DEVICE/MANUFACTURER DEPENDENT** — varies by OEM or Android version.
- **NOT POSSIBLE FOR NORMAL THIRD-PARTY APPS** — not attempted.

## GUARANTEED BY ANDROID API (with Device Owner)

- Only allowlisted packages can run in lock task mode.
- Notifications and Overview are unavailable when their lock task features are not granted.
- A Device Owner app cannot be uninstalled, and Device Owner cannot be removed from Settings.
- User restrictions: no safe boot, no force-stop/clear-data/disable, no uninstall, no manual
  date/time changes; in Strict mode also no installs, no new users, no user switching and (release
  builds) no debugging.
- Restrictions and the persistent preferred HOME activity survive reboots.
- Provisioning Device Owner requires an explicit, privileged step (ADB, QR, NFC, zero-touch or EMM)
  on a device without accounts.

## SUPPORTED

- **Persistent HOME activity:** brings the lockdown screen back on Home and after boot.
- **`BOOT_COMPLETED`:** delivered after the user unlocks the device once; Lockdown App is not
  direct-boot aware, so nothing runs before the first unlock (the device is locked anyway).
- **Exact alarms:** `SCHEDULE_EXACT_ALARM` is user-grantable and **denied by default on
  Android 14+** for newly installed apps. Without it, alarms are inexact (observed window about
  80 seconds on the API 35 emulator). A Device Owner cannot grant this app-op through
  `setPermissionGrantState`. `USE_EXACT_ALARM` is reserved by Play policy for alarm/calendar apps
  and is not used.
- **Screen pinning (no Device Owner):** the user confirms it and can leave it (Back + Overview,
  or as configured on the device). Lockdown App counts exit attempts and offers a way back through
  its notification, but cannot prevent leaving.
- **Background activity starts:** Device Owner apps are exempt. Ordinary apps cannot bring the
  lockdown screen back from the background on Android 10+.

## DEVICE/MANUFACTURER DEPENDENT

- Aggressive battery management (e.g. some Xiaomi, Huawei, Honor, OPPO, vivo, realme, OnePlus,
  Meizu, ASUS builds) can delay alarms or broadcasts. Diagnostics warn and link to battery settings.
- Power-menu contents (Emergency, Restart, Lockdown) and emergency-dialer entry points differ by OEM.
- Some OEMs add their own gestures or panels. Lock task generally disables them, but this is not
  verified on every device.
- Whether `am force-stop`/`am kill` on a lock task Device Owner app is honoured may differ. The
  API 35 emulator ignored `force-stop` during a session.
- Boot receiver delivery can be delayed on some devices. The HOME override covers this.

## NOT POSSIBLE FOR NORMAL THIRD-PARTY APPS

- Blocking or delaying **emergency calls** — and Lockdown App would not do it anyway.
- Blocking **recovery mode**, bootloader access or fastboot.
- Preventing **factory reset from recovery mode**, or surviving a factory reset.
- Preventing the device from being **powered off** (the power menu stays available by design).
- Enforcing anything **without** Device Owner beyond screen pinning.
- Making an existing consumer phone with accounts a Device Owner without a reset.
- Detecting time spent in other apps without the `PACKAGE_USAGE_STATS` special permission (not
  requested). The dashboard therefore reports focused and planned time, not "distraction time".
- Using accessibility services, overlays or hidden APIs to simulate control. Deliberately excluded.

## Remaining escape paths (by design)

| Path | Why it remains |
|---|---|
| Factory reset (recovery mode) | Android recovery guarantee |
| Using an allowed app's own features | The user chose to allow it |
| Removing Device Owner between sessions | Voluntary before a session starts |
| Skipping or cancelling before the start | Voluntary before a session starts |
| ADB on a debug build | Developer recovery path; release Strict sessions disable debugging |
| No Device Owner | Screen pinning can be exited |
| Clock change + reboot without Device Owner | The monotonic anchor resets on reboot |
