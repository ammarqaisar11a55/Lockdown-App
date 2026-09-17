# Lockdown App

Lockdown App is an Android app that puts the phone into a **scheduled, hard-to-escape focus
lockdown** — for example every weekday from 08:00 to 13:00. During a session only the apps you
allow can be opened, Home returns to the focus screen, and the session cannot be ended from the app.

It is built entirely on official Android device-management APIs (Device Owner, lock task mode,
user restrictions). No root, no accessibility-service tricks, no overlays, no hidden APIs.

> **Honest scope.** Lockdown App makes voluntary escape very difficult; it does not and cannot control
> every part of Android. Emergency calls, the power menu, the secure lock screen, recovery mode and
> factory reset always remain available. See [Limitations](#limitations).

---

## Overview

| Feature | Details |
|---|---|
| Schedules | One-time, daily, weekdays, custom days; several sessions per day; overnight sessions; overlap prevention |
| Focus now | Ad-hoc 25/50/90-minute sessions |
| Enforcement | Device Owner lock task (kiosk) mode with an allowlist, HOME override, uninstall block and user restrictions; screen-pinning fallback without Device Owner |
| Strict mode | Unexitable sessions: only available when the app is Device Owner (lock task mode, no unpin gesture), plus extra restrictions (install apps, add/switch users, debugging in release builds) |
| Countdown | Optional cancellable countdown (1/5/10 min) and reminder notification before a session |
| Recovery | Reboot, process death, time/timezone change, missed alarms, policy drift |
| Dashboard | Today's progress, planned vs. focused time, weekly total, completed sessions, exit attempts, upcoming sessions |
| History | Per-session start/end, duration, completed/interrupted, recoveries, exit attempts |
| Diagnostics | Device setup screen with capability checks and provisioning instructions |
| Offline | No network permission, no account, no backend |

## Architecture

```
UI (Compose screens)
 ↓
ViewModels (StateFlow, lifecycle-aware collection)
 ↓
Use cases / pure domain logic (ScheduleCalculator, LockdownDecider, SessionClock, …)
 ↓
Repositories (Room, DataStore)          Device layer (DevicePolicyController, AlarmManager)
```

The heart of the app is the **Policy Reconciliation Engine**
(`core/security/PolicyReconciliationEngine.kt`):

```
persisted state + current time + device policy state → reconciliation → correct device state
```

Every trigger — alarm, boot, process start, time change, app foreground, lockdown-screen timer —
calls the same idempotent, serialized `reconcile()`. State is persisted before device policy
changes, so a crash at any point is repaired on the next run.

Details: [docs/architecture.md](docs/architecture.md) · [docs/scheduling.md](docs/scheduling.md)

## Requirements

| | |
|---|---|
| Android | 9 (API 28) or newer — API 28 is required for `setLockTaskFeatures` |
| Target / compile SDK | 35 |
| Build | JDK 17, Android SDK platform 35, Gradle 8.10.2 (wrapper), AGP 8.7.3, Kotlin 2.0.21 |
| Libraries | Jetpack Compose + Material 3, Navigation, Lifecycle, Room, DataStore, Hilt, Coroutines |

## Device Owner Setup

Strong enforcement requires Lockdown App to be the **Device Owner**. Android only allows this on a
device with no accounts (typically freshly reset). For development:

```bash
./gradlew :app:installDebug
```

```bash
adb shell dpm set-device-owner com.example.focuslock.debug/com.example.focuslock.core.device.FocusDeviceAdminReceiver
```

For a release build the component is `com.example.focuslock/.core.device.FocusDeviceAdminReceiver`.
Removal, troubleshooting and production provisioning (QR code / zero-touch):
[docs/device-owner-setup.md](docs/device-owner-setup.md).

## Lock Task Setup

No manual lock task configuration is needed: during a session the Device Owner allowlists
Lockdown App, the default dialer and your allowed apps, sets the lock task features, and registers
the lockdown screen as the persistent HOME activity. Everything is removed when the session ends.
See [docs/kiosk-mode.md](docs/kiosk-mode.md).

## Running Locally

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:installDebug
```

The debug build (`com.example.focuslock.debug`) includes **Settings › Developer tools**: a 60-second
test lockdown, simulated boot recovery, a schedule that starts in ~2 minutes, and a data reset.
These tools are compiled only from `app/src/debug` and do not exist in release builds.

Release builds are signed only if an untracked `keystore.properties` exists
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); otherwise an unsigned APK is produced.

```bash
./gradlew :app:assembleRelease
```

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:lintDebug
```

```bash
./gradlew :app:connectedDebugAndroidTest
```

Device Owner instrumentation tests run only when the debug app is provisioned as Device Owner on
the test device; otherwise they are skipped. Full test plan and manual scenarios:
[docs/testing.md](docs/testing.md).

## Limitations

Lockdown App distinguishes four capability levels throughout the docs:

- **GUARANTEED BY ANDROID API** — enforced by the platform once configured (e.g. lock task allowlist).
- **SUPPORTED** — available through public APIs, with documented caveats.
- **DEVICE/MANUFACTURER DEPENDENT** — behaviour varies by OEM or Android version.
- **NOT POSSIBLE FOR NORMAL THIRD-PARTY APPS** — cannot be done without abusing the platform, and is not attempted.

Key points:

- Without Device Owner, sessions use **screen pinning**, which the user can always exit — Android
  guarantees this and no app can override it. For that reason **Strict mode is only offered on a
  Device Owner phone**; elsewhere the app points to Android's "Ask for PIN before unpinning" option.
- Emergency calls, the power menu, the secure lock screen, recovery mode and **factory reset** are
  never blocked. A factory reset removes Lockdown App.
- On Android 14+, exact alarms are denied by default; until the user allows them a session may start
  up to about a minute late (the end is still enforced on time by the lockdown screen).
- OEM battery management may delay background work.

Full list: [docs/android-limitations.md](docs/android-limitations.md).

## Security Model

Threats T1–T14 (Home, Recents, launching apps, uninstall, Settings, reboot, process death, offline,
time changes, permissions, notifications, launcher changes, device-admin removal, battery kills)
are each documented with the Android capability used, the mitigation, the remaining limitation and
a test procedure.

- [docs/security-model.md](docs/security-model.md) — principles, trust boundaries, data handling
- [docs/threat-model.md](docs/threat-model.md) — the T1–T14 threat table

## Production Deployment

- Distribute Lockdown App to devices that are **provisioned during setup** (QR code, NFC, zero-touch or
  an EMM). Consumer phones that already have accounts cannot become fully managed without a reset.
- The app implements the Android 10+ provisioning handshake (`GET_PROVISIONING_MODE`,
  `ADMIN_POLICY_COMPLIANCE`) and supports fully managed mode only.
- The product name is "Lockdown App" (`app_name` in `strings.xml`); the technical package id is still
  the placeholder `com.example.focuslock`.
- Replace the placeholder identity: `applicationId`/`namespace` in `app/build.gradle.kts`, the
  hard-coded names in `AndroidDevicePolicyController.HOME_ALIAS_CLASS`, the lockdown
  `taskAffinity` in the manifest, and `app_name` in `strings.xml`.
- Sign with your own key; `keystore.properties` and keystores are git-ignored.
- Google Play restricts Device Owner distribution; check the current Play policy for
  device-management apps.

See [docs/device-owner-setup.md](docs/device-owner-setup.md#production-deployment).

## Design

The UI implements the "Industry" design system from the Claude Design handoff: a steel-blue accent
(`#5980A6`) on a light technical ground (`#F2F2F3`), Barlow Condensed headings over Barlow body
text, hairline-bordered cards, a blueprint-framed "Focus today" card with registration marks, and
Lucide icons at stroke 1.5. Light, Dark and System appearance are selectable in
**Settings › Appearance**; the lockdown screen is always dark with a slow breathing glow (static when
system animations are turned off).

- Tokens: `ui/theme/Theme.kt` (`LockdownColors`, `LockdownType`, `LockdownScreenColors`)
- Shared components: `ui/components/Components.kt`, icons in `ui/components/LucideIcons.kt`
- Fonts: Barlow and Barlow Condensed are bundled in `app/src/main/res/font` under the SIL Open Font
  License (`third_party/barlow/OFL.txt`), so no network access is needed.

## Project Structure

```
app/src/main/java/com/example/focuslock/
├── core/         common, device (DPM), notification, scheduling, security (engine), time
├── data/         local (Room), preferences (DataStore), repository
├── domain/       model, repository interfaces, use cases
├── feature/      onboarding, dashboard, schedule, lockdown, applications, history, settings, deviceowner
├── receiver/     boot, time change, alarm, notification action receivers
├── ui/           theme, shared components, navigation
└── di/           Hilt modules
app/src/debug/    developer tools (debug builds only)
app/src/release/  empty developer-tools stand-in
```
