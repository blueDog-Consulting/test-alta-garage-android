#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="dev.bluedog.garagedoor"

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK"
  echo "Run ./gradlew assembleDebug first."
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device connected."
  exit 1
fi

echo "Installing $APK as Play Store package..."
adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
adb push "$APK" /data/local/tmp/garage-unlock.apk
adb shell pm install -i com.android.vending -r -t /data/local/tmp/garage-unlock.apk
adb shell rm /data/local/tmp/garage-unlock.apk

echo "Launching app once on phone..."
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

echo "Restarting Android Auto..."
adb shell am force-stop com.google.android.projection.gearhead

echo
echo "Done. On your phone:"
echo "  1. Open Android Auto settings -> Customize launcher"
echo "  2. Look for Garage Unlock"
echo
echo "Installer source:"
adb shell dumpsys package "$PACKAGE" | rg 'installerPackageName=' || true
