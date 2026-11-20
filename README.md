# HelloDolby

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="128" height="128" alt="HelloDolby Logo" />
</p>

<p align="center">
  <b>Modern Open-Source Dolby Atmos &amp; Xiaomi Sound Controller for AOSP</b><br />
  Designed with Miuix HyperOS Aesthetics &bull; Dynamic Material You Theming &bull; Hardware AIDL DSP Integration
</p>

---

## 📖 Overview

**HelloDolby** (`com.puppyfrommars.hellodolby`) is an open-source audio enhancement controller crafted for Xiaomi devices running AOSP and custom ROMs (such as Evolution X). Built from the ground up using Jetpack Compose and the [Miuix](https://github.com/compose-miuix-ui/miuix) design system, HelloDolby directly interfaces with the Snapdragon 8 Elite hardware DSP (`SM8850`) and AIDL audio effects layer to deliver an authentic Dolby Atmos and Xiaomi Sound experience without proprietary MIUI framework dependencies.

---

## ✨ Features

- **Audio Processing Engine Selection**:
  - **Dolby Atmos**: Hardware-accelerated Dolby Audio Processing (DAP) offload engine for cinematic 3D soundstages.
  - **Xiaomi Sound**: High-fidelity Xiaomi acoustic tuning (MiSound FX).
  - **Direct / Pure DAC (Off)**: Bit-perfect passthrough without DSP coloring.
- **Dolby Audio Profiles**:
  - Instant preset switching: **Dynamic**, **Movie**, **Music**, and **Voice**.
- **Spatial Audio &amp; Virtualizer**:
  - **Immersive Sound**: Native AOSP Spatializer integration for multi-channel spatialized audio.
  - **Surround Sound (Virtualizer)**: Stereo acoustic separation and soundstage expansion with real-time 0–100% intensity slider.
- **Quick Settings Tile**:
  - **"Dolby Atmos"** quick tile in the notification shade to toggle between Dolby Atmos enabled and disabled (fallback to Xiaomi Sound) with active preset subtitles.
- **Miuix HyperOS Design Language**:
  - Smooth rounded squircles, tactile cards, and fluid animations.
  - **Floating Bottom Bar**: Translucent liquid-glass floating toolbar for effortless navigation between **Effects** and **Contributors** screens.
- **Material You Dynamic Theming (Monet)**:
  - Wallpaper-based dynamic MD3 color palette extraction (`MonetLight`, `MonetDark`, `MonetSystem`).
  - **Theme Toggle**: In-app header action to switch between Light, Dark, and Follow System modes.
  - **Intelligent Status Bar Insets**: High-contrast status bar and navigation bar rendering (crisp dark icons on light backgrounds, white icons on dark backgrounds).
- **Original Copyright-Safe Branding**:
  - Custom acoustic wave squircle iconography across all mipmap densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).

---

## 📱 Hardware &amp; Architecture

- **Primary Target Device**: Xiaomi 17 (Codename: `pudding` / Platform: `SM8850` Snapdragon 8 Elite)
- **OS Platform**: Android 15 / Android 17 (Evolution X 12.x / LineageOS 24.0)
- **Audio Effect UUIDs**:
  - Dolby DAP Offload: `9d4921da-8225-4f29-aefa-39537a04bcaa`
  - Xiaomi MiSound FX: `5b8e36a5-144a-4c38-b1d7-0002a5d5c51b`
- **Privileged Permissions**:
  - `android.permission.MODIFY_AUDIO_SETTINGS`
  - `android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS`
  - `android.permission.WRITE_SECURE_SETTINGS`
  - `android.permission.BIND_QUICK_SETTINGS_TILE`

---

## 🛠️ Installation Guide

### Method 1: Standalone Root APK Installation (Live Testing)

For quick deployment and live testing on a rooted device (KernelSU / APatch / Magisk):

1. **Build and Sign the APK**:
   ```bash
   ./build-apk.sh
   ```
   This script builds the debug APK, zipaligns it, and signs it with the AOSP platform keys.

2. **Install via ADB**:
   ```bash
   adb install -r -d -g apk/HelloDolby.apk
   ```

3. **Grant Privileged Permissions**:
   Since the app accesses `MODIFY_DEFAULT_AUDIO_EFFECTS` and `WRITE_SECURE_SETTINGS`, grant root permissions via KernelSU/Magisk or grant via ADB shell:
   ```bash
   adb shell pm grant com.puppyfrommars.hellodolby android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS
   adb shell pm grant com.puppyfrommars.hellodolby android.permission.WRITE_SECURE_SETTINGS
   ```

4. **Launch HelloDolby**:
   ```bash
   adb shell am start -n com.puppyfrommars.hellodolby/.ui.MainActivity
   ```

---

### Method 2: AOSP Custom ROM Source Integration

To integrate HelloDolby as a prebuilt privileged system application inside your AOSP / Evolution X build tree:

1. **Place Repository in Hardware Path**:
   ```bash
   # In the root of your Android build tree:
   git clone https://github.com/marcmyworld/hardware_HelloDolby.git hardware/HelloDolby
   ```

2. **Inherit in Device Makefile (`device.mk`)**:
   Add the following line to `device/xiaomi/pudding/device.mk` (or your device makefile):
   ```make
   $(call inherit-product, hardware/HelloDolby/hellodolby.mk)
   ```

3. **Build the ROM**:
   ```bash
   source build/envsetup.sh
   lunch lineage_pudding-userdebug
   m bacon
   ```

During compilation, Soong will include `HelloDolby` in `/system_ext/priv-app/HelloDolby/` signed with the platform certificate, along with `/system_ext/etc/permissions/privapp-permissions-hellodolby.xml`.

---

## 👥 Contributors &amp; Credits

- **Lead Developer &amp; Maintainer**:
  - **marc** ([@marcmyworld](https://github.com/marcmyworld))
  - Telegram: [@xeon_man](https://t.me/xeon_man)
- **UI Design System**:
  - [Miuix](https://github.com/compose-miuix-ui/miuix) &bull; Compose Multiplatform Miuix UI

---

## 📄 License

```text
SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
SPDX-License-Identifier: Apache-2.0
```
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0).
