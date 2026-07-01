#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail

LOG="${1:-android-auto-diagnose.log}"
PACKAGE="dev.bluedog.garagedoor"

if ! adb get-state >/dev/null 2>&1; then
  echo "Connect your phone with USB debugging enabled."
  exit 1
fi

echo "Writing logs to $LOG"
echo
echo "On your phone NOW:"
echo "  Settings -> Connected devices -> Android Auto -> Customize launcher"
echo
echo "Leave that screen open for 10 seconds..."
echo

adb logcat -c
adb logcat -v time > "$LOG" &
LOG_PID=$!
sleep 12
kill "$LOG_PID" 2>/dev/null || true

echo
echo "=== Installer metadata ==="
adb shell dumpsys package "$PACKAGE" 2>/dev/null | rg -i 'installerPackage|CarAppService|category\.|versionName|enabled=' || true

echo
echo "=== Play Protect sees (during adb install this is usually com.android.shell) ==="
rg -i 'VerifyApps: Installer app|bluedog\.garagedoor|garage\.unlock|template apps found|ProjectionApp|AndroidAutoCompatible|AppValidation|isPackageInstalledByPlay|Play Store isPackage|blocked_packages|GH\.AppInstallerUtil' "$LOG" || true

echo
echo "=== Android Auto developer settings ==="
adb shell dumpsys activity service com.google.android.projection.gearhead 2>/dev/null | rg -i 'allow_unknown|car_backup_valid' || true

echo
echo "Full log saved to $LOG"
