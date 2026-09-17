# Device Owner Setup

FocusLock never acquires Device Owner on its own. It must be provisioned deliberately.

## Component names

| Build | Device admin component |
|---|---|
| debug | `com.example.focuslock.debug/com.example.focuslock.core.device.FocusDeviceAdminReceiver` |
| release | `com.example.focuslock/.core.device.FocusDeviceAdminReceiver` |

The in-app **Device setup** screen always shows the exact command for the installed build.

## Development / testing (ADB)

Requirements:

- An emulator or test device you are willing to reset.
- **No accounts** on the device (Settings › Passwords & accounts). A freshly reset device where
  account setup was skipped works; a fresh emulator works.
- No other Device Owner or work profile, and a single user.
- USB debugging enabled.

Steps:

1. Install the app.

   ```bash
   ./gradlew :app:installDebug
   ```

2. Provision it.

   ```bash
   adb shell dpm set-device-owner com.example.focuslock.debug/com.example.focuslock.core.device.FocusDeviceAdminReceiver
   ```

   Expected output: `Success: Device owner set to package …`

3. Verify.

   ```bash
   adb shell dpm list-owners
   ```

4. Open FocusLock › Settings › Device setup. *Device Owner* and *Lock task* should read Ready.

Common errors:

| Message | Cause / fix |
|---|---|
| `Not allowed to set the device owner because there are already some accounts on the device` | Remove all accounts or factory reset |
| `Trying to set the device owner, but device owner is already set` | Another DPC owns the device; reset |
| `Unknown admin` | App not installed, or wrong component name (debug vs release) |
| `Not allowed to set the device owner because there are already several users` | Remove secondary users / work profile |

Also recommended on Android 14+: allow exact alarms
(Device setup › Exact alarms › Fix, or `adb shell appops set <package> SCHEDULE_EXACT_ALARM allow`).

## Removing Device Owner during development

In order of preference:

1. **In the app:** Settings › *Remove device management* (only when no session is active). This
   calls `DevicePolicyManager.clearDeviceOwnerApp()`; the system also drops all restrictions set
   by FocusLock.
2. **ADB:** `adb shell dpm remove-active-admin <component>` — works only for apps installed with
   `android:testOnly="true"` (for example when run from Android Studio).
3. **Factory reset** (always works; emulator: *Wipe data*).

Uninstalling a Device Owner app is blocked by Android until Device Owner is removed.

Debug builds never apply `no_debugging_features`, so ADB remains available during Strict sessions
on test devices. Release Strict sessions do disable debugging; note that Android does not re-enable
USB debugging automatically when that restriction is lifted.

## Limitations on existing consumer devices

- A phone that already has a Google (or other) account cannot become Device Owner without a
  factory reset. This is an Android security guarantee, not a FocusLock limitation.
- Profile Owner (work profile) mode does not provide device-wide lock task, so FocusLock does not
  support it.
- Without Device Owner, FocusLock still works in **screen pinning** mode, which the user can exit.

## Production deployment

- **Provision at setup time** with one of Android's managed-provisioning methods:
  - **QR code** (tap the welcome screen six times): include
    `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME`,
    `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION` and
    `android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` (SHA-256 of the signing
    certificate, URL-safe Base64);
  - NFC provisioning;
  - Zero-touch enrollment or an EMM that supports custom DPCs.
- FocusLock implements the Android 10+ provisioning handshake:
  `GET_PROVISIONING_MODE` returns *fully managed device*, and `ADMIN_POLICY_COMPLIANCE` completes
  immediately. Both activities require `BIND_DEVICE_ADMIN`, so only the system can start them.
- Rename the product identity before shipping (see README › Production Deployment) and sign with
  your own key.
- Plan how users leave management. The supported paths are the in-app removal (outside sessions)
  and factory reset.
- Check the current Google Play policy for device-owner apps; many deployments distribute DPCs
  outside Play or through managed Google Play.
