# PerApp Volume Control Enhanced

A root-required Android app + Magisk module for per-app volume control, designed for Android 15 and intended to support older versions where possible. It even allows audio control for apps running on other Android profiles (work profiles, second space, Island).

> Previously known as *Individual App Audio Volume Control* — the repository was renamed to **PerApp Volume Control Enhanced**.

## Features

### Per-app volume control
- Active playback list with per-app volume sliders (control the volume of individual apps, independent of the master volume).
- Live tracking of active audio sessions via hidden audio APIs.
- Per-app mute and percentage shortcuts on every slider row.

### Volume-button overlay
- Press the volume buttons to open a floating overlay with per-app sliders (requires the accessibility volume-key listener + overlay permission).
- Snooze timer, tap-outside-to-dismiss, pause/resume, and focused-app blocklist to suppress the overlay while a chosen app is in the foreground (usage access required).
- Overlay ignore list (System UI, Face Unlock label, fused location, LineageOS settings, etc. are ignored by default).
- Opens App Info when you tap an app name; long-press copies the package name.

### Notification controls
- Foreground notification with per-app volume rows while audio is active.
- Next/Prev paging when more apps are active, mute and 100% quick buttons, profile labels for cross-profile entries.

### Presets & rules
- Preset list with search, quick actions, and import/export.
- Manage Apps screen to assign presets per profile (work/personal).
- Rules screen for playback overrides (location geofencing removed; storage + playback overrides groundwork in place).

### Diagnostics & setup
- Setup Check screen with an all-yes checklist: privileged flag, WRITE_SECURE_SETTINGS, hidden API policy, usage access, overlay/accessibility status.
- Diagnostics screen (searchable) with permission status, hidden API status, cross-profile probes, and notification-persistence checks.
- Cross-profile playback control for foreign-profile packages (work profile / second space / Island) via `INTERACT_ACROSS_USERS(_FULL)`.

### UI
- Apple-style "liquid glass" restyle (translucent frosted panels, glass buttons/sliders, API 31+ blur) with a "Reduce effects" switch for devices without blur support.

## Installation guide

### Requirements
- A rooted device (Magisk or KernelSU) on Android 15 (and as far back as supported).
- The module installs the app as a **privileged system app**, which is required for the hidden audio APIs and per-user (cross-profile) control.

### Install via Magisk / KernelSU
1. Download the latest `IndividualAppAudioVolumeControl-magisk.zip` from the [Releases](https://github.com/vmahin/PerAppVolumeControlEnhanced/releases) page.
2. Open Magisk (or KernelSU manager) → Modules → Install from storage → select the downloaded ZIP.
3. Reboot the device.
   - On **KernelSU**, install a working metamodule first (for example `meta-overlayfs`) before flashing this module. Without a metamodule, KernelSU does not mount the module `system/` tree, so the APK will not become a real priv-app and the privapp permission XML will not be applied — privileged features, including cross-profile detection via `INTERACT_ACROSS_USERS`, can fail even if the module ZIP appears installed.
4. Open the app (label: **IndividualAppVolumeControl**, installed package `fuck.individualappaudiovolumecontrol.droidvendorssuck`).
5. Open **Setup Check** and confirm every item reads **yes**:
   - The module grants `INTERACT_ACROSS_USERS`, `INTERACT_ACROSS_USERS_FULL`, and `WRITE_SECURE_SETTINGS` at boot (`module/service.sh`), and enables the `GET_USAGE_STATS` appop so the app appears in Settings → Usage access with access on.
   - If any item still reads *no*, re-flash the module and reboot; on some ROMs the privileged grants need the privapp allowlist (already shipped in the module) to be applied by the package manager.
6. Optional features:
   - **Volume overlay**: enable the overlay permission and turn on the accessibility service (*IndividualAppVolumeControl: Volume button overlay*) in Settings → Accessibility.
   - **Focused-app blocklist**: grant Usage access in Settings → Usage access.
   - **Cross-profile control**: requires the module to be mounted as a real priv-app with `INTERACT_ACROSS_USERS(_FULL)` granted (see KernelSU metamodule note above).

### Build from source
See [docs/BUILD.md](docs/BUILD.md):
- Build the signed modern release APK, then run `module/package.sh` to produce `IndividualAppAudioVolumeControl-magisk.zip`, or use `scripts/build-module.sh` / `./gradlew :app:build` for the one-step build + module.
- The APK is mounted by the module at `system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk`.

## Notes
- The app relies on hidden audio APIs to set per-player volume; root + privileged installation is therefore mandatory.
- Volume control is applied per active playback configuration.
- For bootloop triage, the module logs early-boot logcat to `/data/adb/individualappaudiovolumecontrol-boot.log` (see [docs/BUILD.md](docs/BUILD.md)).

## Status
- Core service, UI controls, diagnostics/setup check, liquid-glass UI, cross-profile control, and Magisk module packaging in place; v0.1.2 is the current release.

## Repository layout
- `app/`: Android app (system/privileged app)
- `module/`: Magisk module that installs the app as a system-privileged app
- `docs/`: design, research, build, compatibility, and validation notes
- `docs/COMPATIBILITY.md`: device testing matrix
- `docs/PRESETS.md`: preset export/import format
- `TODO.md`: prioritized tasks
- `STATUS.md`: current work log
