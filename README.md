# HelloDolby

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="128" height="128" alt="HelloDolby Logo" />
</p>

<p align="center">
  <b>Modern Open-Source Sound &amp; Dolby Atmos Controller for AOSP</b><br />
  Designed with Miuix HyperOS Aesthetics &bull; Dynamic Material You Theming &bull; Hardware AIDL Audio DSP Integration
</p>

---

## Overview

**HelloDolby** (`com.puppyfrommars.hellodolby`) is an open-source audio enhancement controller crafted for modern Android devices running AOSP and custom ROMs (Evolution X, LineageOS, etc.). Built with Jetpack Compose and the [Miuix](https://github.com/compose-miuix-ui/miuix) design system, HelloDolby interfaces directly with the hardware DSP and Android AIDL audio effects layer to deliver an authentic Dolby Atmos and audio enhancement experience without proprietary framework dependencies.

---

## Key Features

- **Audio Engine Selection**: Seamless switching between **Dolby Atmos** (hardware DAP offload), **Xiaomi Sound** (MiSound FX), and **Pure DAC** (uncolored passthrough).
- **Dolby Audio Presets**: Hardware-accelerated profiles (**Dynamic**, **Movie**, **Music**, **Game**, **Voice**, and **Custom**) with interactive spotlight carousel and quick-switch chips.
- **Standalone Enhancers**: Independent controls for **Dialogue Enhancer**, **Volume Leveler**, and **Bass Enhancer** directly modulating hardware DSP parameters.
- **Spatial & Immersive Audio**: AOSP Spatializer and Dolby DAP surround coordination tailored to preserve speaker acoustics and prevent phase cancellation.
- **Fine Tune Equalizer**: 10-band tactile vertical sliders, real-time spline frequency response curve, custom user presets, and built-in offline AutoEQ catalog (8,800+ headphone profiles).
- **Multi-Device State Persistence**: Independent profile memory per output device (Speaker, 3.5mm Headset, USB DAC, Bluetooth) saved and restored automatically across reboots.

---

## Building & Installation

### Standalone Build (Gradle)
Compile the standalone APK signed with standard AOSP platform keys:
```bash
./build-apk.sh
```
The output APK will be generated at `prebuilt/HelloDolby.apk`.

### AOSP ROM Integration
Include HelloDolby in your device tree by adding the following line to `device.mk`:
```makefile
$(call inherit-product-if-exists, hardware/HelloDolby/hellodolby.mk)
```
The AOSP Soong build system imports the platform-signed prebuilt from `prebuilt/HelloDolby.apk` and installs it as a privileged system application with required audio effect permissions.

---

## License

```
Copyright (C) 2026 marcmyworld <moviesminister@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
