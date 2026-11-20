# HelloDolby

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="128" height="128" alt="HelloDolby Logo" />
</p>

<p align="center">
  <b>Modern Open-Source Dolby Atmos &amp; Audio Enhancement Controller for AOSP</b><br />
  Designed with Miuix HyperOS Aesthetics &bull; Dynamic Material You Theming &bull; Hardware AIDL DSP Integration
</p>

---

## 📖 Overview

**HelloDolby** (`com.puppyfrommars.hellodolby`) is an open-source audio enhancement controller crafted for modern Android devices running AOSP and custom ROMs (such as Evolution X, LineageOS, etc.). Built from the ground up using Jetpack Compose and the [Miuix](https://github.com/compose-miuix-ui/miuix) design system, HelloDolby interfaces directly with the hardware DSP and Android AIDL audio effects layer to deliver an authentic Dolby Atmos and audio enhancement experience without proprietary framework dependencies.

---

## ✨ Features

- **Audio Engine Selection & Dynamic 16:9 Artwork Banners**:
  - **Dolby Atmos**: Hardware-accelerated Dolby Audio Processing (DAP) offload engine for cinematic 3D soundstages, paired with a custom holographic 3D spatial sphere banner.
  - **Xiaomi Sound**: High-fidelity Xiaomi acoustic tuning (MiSound FX) accompanied by an audiophile glowing vacuum tube artwork banner.
  - **Direct / Pure DAC (Off)**: Bit-perfect uncolored DAC passthrough, represented by a precision DAC ladder and pure sine banner.
- **Dolby Audio Presets & Overhauled UI**:
  - 6 distinct hardware-accelerated profiles: **Dynamic**, **Movie**, **Music**, **Game**, **Voice**, and **Custom**.
  - **Spotlight Pager Carousel**: Center-spotlight swipeable banner carousel highlighting the active preset with smooth scaling, depth, and snap-to-center physics.
  - **1-Tap Quick-Switch Chips**: Clean horizontal pill filter chips directly beneath the carousel for instantaneous 1-tap preset jumps, bidirectionally synchronized with the carousel.
- **Standalone Audio Enhancers (Dolby DAP Direct)**:
  - **Dialogue Enhancer**: Isolate and elevate foreground speech and podcast intelligibility with 0–100% intensity slider.
  - **Volume Leveler**: Dynamic range compression and loudness normalizer with 0–100% intensity slider.
  - **Bass Enhancer**: Hardware dynamic sub-bass harmonic synthesis with 0–100% intensity slider.
  - **100% Standalone**: Direct hardware DSP parameters that operate independently of whether the graphic equalizer is active or off.
- **Spatial Audio &amp; Virtualizer**:
  - **Immersive Sound**: Synchronized with AOSP Spatializer compatibility registry across Speaker, Wired, USB, and Bluetooth endpoints, coordinating with Dolby to prevent double-spatialization artifacts.
  - **Surround Sound (Virtualizer)**: Stereo acoustic separation and soundstage expansion with real-time 0–100% intensity slider, tuned to preserve in-phase acoustic low-end on physical phone speakers.
- **Fine Tune (Dedicated Equalizer Engine)**:
  - **10-Band Vertical Sliders**: Side-by-side vertical tactile sliders from sub-bass (32 Hz) to brilliance treble (16 kHz) with centered 0 dB notches and live dB readouts.
  - **Interactive Live EQ Graph**: Spline-interpolated live frequency response curve with draggable control points and gradient fill, switchable on the fly with a single rotating animated icon button.
  - **MiSound-Style Image Preset Cards**: Rich carousel with customized background artwork for Flat, Rock, Jazz, Pop, Classical, Hip Hop, Electronic, Vocal Boost, and Bass Boost.
  - **Custom Preset Management**: "Save Current" button and "Manage" popup dialog to apply, rename, and delete custom user equalizer presets with high-contrast text fields in both dark and light themes.
  - **AutoEQ Integration**: Bundled offline catalog of 8,853 measured headphone and earbud target curves with multi-token search and 1-tap download and application.
  - **Dedicated Reset Section**: Independent actions visible at all times to reset equalizer bands to neutral (0.0 dB) or reset the entire Dolby Audio DSP engine.
  - **Graceful Disabled State**: Equalizer controls grey out when disabled while preserving smooth vertical touch scrolling across the screen.
- **Multi-Device Profile Memory &amp; Persistence**:
  - Automatically detects connected audio outputs: Phone Speaker, Bluetooth Headsets, 3.5mm Wired Headphones, and USB Audio DACs.
  - Independently saves and restores snapshots per device without mixing configurations.
  - Seamless persistence across reboots via `BootCompletedReceiver`.
- **Quick Settings Tile**:
  - **"Dolby Atmos"** quick tile in the notification shade to toggle between Dolby Atmos enabled and disabled with active preset subtitles across all profiles.
- **Adaptive Icon &amp; Material You Theming**:
  - Android 13+ adaptive icon with full-bleed background, balanced safe-margin glyph, and monochrome themed icon support.
  - Dynamic Material You Monet wallpaper palettes (`MonetLight`, `MonetDark`, `MonetSystem`).
  - Animated top theme switcher (Sun for Light, Moon for Dark, Auto-A for System).
  - Three-tab bottom navigation: **Audio Engine**, **Fine Tune**, and **About**.

---

## 📱 Hardware &amp; Architecture

- **Target Platforms**: Android devices with AIDL Dolby Atmos / DAP offload HAL support (Snapdragon / MediaTek)
- **OS Platform**: Android 14 / 15 / 16 / 17 (AOSP, Evolution X, LineageOS, etc.)
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

For quick deployment and live testing on any rooted device (KernelSU / APatch / Magisk):

1. **Build and Sign the APK**:
   ```bash
   ./build-apk.sh
   ```
   This script automatically builds the debug APK, dynamically discovers SDK build-tools or fetches standalone signing tools, signs with AOSP platform keys, and installs if a device is connected.

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

To integrate HelloDolby as a prebuilt privileged system application inside your AOSP / ROM build tree:

1. **Place Repository in Hardware Path**:
   ```bash
   # In the root of your Android build tree:
   git clone https://github.com/marcmyworld/HelloDolby.git hardware/HelloDolby
   ```

2. **Inherit in Device Makefile (`device.mk`)**:
   Add the following line to `device/<vendor>/<codename>/device.mk` (or your device makefile):
   ```make
   $(call inherit-product, hardware/HelloDolby/hellodolby.mk)
   ```

3. **Build the ROM**:
   ```bash
   source build/envsetup.sh
   lunch <target_lunch_combo>
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
