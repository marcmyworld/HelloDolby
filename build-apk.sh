#!/usr/bin/env bash
#
# SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
# SPDX-License-Identifier: Apache-2.0
#

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

BUILD_TOOLS_DIR="$DIR/.build_tools"
mkdir -p "$BUILD_TOOLS_DIR"

echo "=== Building HelloDolby with Gradle ==="
./gradlew assembleDebug

RAW_APK="$DIR/app/build/outputs/apk/debug/app-debug.apk"
ALIGNED_APK="$DIR/app/build/outputs/apk/debug/app-aligned.apk"
FINAL_APK="$DIR/prebuilt/HelloDolby.apk"

mkdir -p "$DIR/prebuilt"

# Dynamically locate zipalign and apksigner
find_sdk_tool() {
    local tool="$1"
    if command -v "$tool" >/dev/null 2>&1; then
        command -v "$tool"
        return 0
    fi
    for sdk_dir in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "/opt/android-sdk" "$HOME/Android/Sdk"; do
        if [ -n "$sdk_dir" ] && [ -d "$sdk_dir/build-tools" ]; then
            local found
            found=$(find "$sdk_dir/build-tools" -name "$tool" -type f 2>/dev/null | sort -V | tail -n 1 || true)
            if [ -n "$found" ] && [ -x "$found" ]; then
                echo "$found"
                return 0
            fi
        fi
    done
    return 1
}

ZIPALIGN=$(find_sdk_tool zipalign || true)
APKSIGNER=$(find_sdk_tool apksigner || true)

# Locate or download standard AOSP platform keys
KEY_DIR="$BUILD_TOOLS_DIR/keys"
if [ -n "${ANDROID_BUILD_TOP:-}" ] && [ -f "$ANDROID_BUILD_TOP/build/make/target/product/security/platform.pk8" ]; then
    PLATFORM_KEY="$ANDROID_BUILD_TOP/build/make/target/product/security/platform.pk8"
    PLATFORM_CERT="$ANDROID_BUILD_TOP/build/make/target/product/security/platform.x509.pem"
elif [ -f "$KEY_DIR/platform.pk8" ] && [ -f "$KEY_DIR/platform.x509.pem" ]; then
    PLATFORM_KEY="$KEY_DIR/platform.pk8"
    PLATFORM_CERT="$KEY_DIR/platform.x509.pem"
else
    mkdir -p "$KEY_DIR"
    echo "=== Fetching standard AOSP platform signing keys into .build_tools/keys ==="
    curl -sL "https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security/platform.pk8?format=TEXT" | base64 -d > "$KEY_DIR/platform.pk8"
    curl -sL "https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security/platform.x509.pem?format=TEXT" | base64 -d > "$KEY_DIR/platform.x509.pem"
    PLATFORM_KEY="$KEY_DIR/platform.pk8"
    PLATFORM_CERT="$KEY_DIR/platform.x509.pem"
fi

echo "=== Aligning and Signing with Platform Keys ==="
rm -f "$ALIGNED_APK" "$FINAL_APK"

if [ -n "$ZIPALIGN" ]; then
    "$ZIPALIGN" -p 4 "$RAW_APK" "$ALIGNED_APK"
else
    echo "Warning: zipalign not found, copying raw APK..."
    cp "$RAW_APK" "$ALIGNED_APK"
fi

if [ -n "$APKSIGNER" ]; then
    "$APKSIGNER" sign --key "$PLATFORM_KEY" --cert "$PLATFORM_CERT" --out "$FINAL_APK" "$ALIGNED_APK"
else
    # Fallback to uber-apk-signer if apksigner binary not available
    UBER_JAR="$BUILD_TOOLS_DIR/uber-apk-signer.jar"
    if [ ! -f "$UBER_JAR" ]; then
        echo "=== Downloading standalone apksigner tool ==="
        curl -sL -o "$UBER_JAR" "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar"
    fi
    java -jar "$UBER_JAR" --apks "$ALIGNED_APK" --key "$PLATFORM_KEY" --cert "$PLATFORM_CERT" --out "$DIR/prebuilt" --overwrite
    mv "$DIR/prebuilt/app-aligned-aligned-signed.apk" "$FINAL_APK" 2>/dev/null || mv "$DIR/prebuilt/app-aligned.apk" "$FINAL_APK"
fi

rm -f "$ALIGNED_APK"

echo "=== Successfully generated $FINAL_APK ==="
