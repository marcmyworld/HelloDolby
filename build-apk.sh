#!/usr/bin/env bash
#
# SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
# SPDX-License-Identifier: Apache-2.0
#

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=== Building HelloDolby with Gradle ==="
./gradlew assembleDebug

RAW_APK="$DIR/app/build/outputs/apk/debug/app-debug.apk"
ALIGNED_APK="$DIR/app/build/outputs/apk/debug/app-aligned.apk"
FINAL_APK="$DIR/apk/HelloDolby.apk"

mkdir -p "$DIR/apk"

APKSIGNER="/opt/android-sdk/build-tools/33.0.1/apksigner"
ZIPALIGN="/opt/android-sdk/build-tools/33.0.1/zipalign"
PLATFORM_KEY="/mnt/android-kitchen/evolution-x/build/make/target/product/security/platform.pk8"
PLATFORM_CERT="/mnt/android-kitchen/evolution-x/build/make/target/product/security/platform.x509.pem"

echo "=== Aligning and Signing with Platform Keys ==="
rm -f "$ALIGNED_APK" "$FINAL_APK"
"$ZIPALIGN" -p 4 "$RAW_APK" "$ALIGNED_APK"
"$APKSIGNER" sign --key "$PLATFORM_KEY" --cert "$PLATFORM_CERT" --out "$FINAL_APK" "$ALIGNED_APK"
rm -f "$ALIGNED_APK"

echo "=== Successfully generated $FINAL_APK ==="

if adb get-state >/dev/null 2>&1; then
    echo "=== Installing to connected ADB device ==="
    adb install -r -d -g "$FINAL_APK"
    echo "=== Launching HelloDolby on device ==="
    adb shell am start -n com.xiaomi.hellodolby/.ui.MainActivity
fi
