# HelloDolby

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="128" height="128" alt="HelloDolby Logo" />
</p>

<p align="center">
  <b>Modern Sound &amp; Dolby Atmos Controller</b><br />
  Designed with Miuix &bull; Dynamic Material You Theming &bull; Hardware AIDL Audio DSP Integration
</p>

---

## Overview

**HelloDolby** is an audio enhancement controller crafted for modern Android devices running AOSP / Custom ROMs. Built with Jetpack Compose and the [Miuix](https://github.com/compose-miuix-ui/miuix) design library, HelloDolby interfaces directly with the hardware DSP and Android AIDL audio effects layer to deliver an authentic Dolby Atmos and audio enhancement experience without proprietary framework dependencies.

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
$(call inherit-product-if-exists, hardware/hellodolby/hellodolby.mk)
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
