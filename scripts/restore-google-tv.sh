#!/usr/bin/env bash
# Undoes set-default-launcher.sh: Google TV's home screen back as the default, Glasshouse just an app again.
# Usage: scripts/restore-google-tv.sh [<tv-ip>:5555]
set -euo pipefail
ADB="$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")"
[[ $# -ge 1 ]] && { "$ADB" connect "$1" >/dev/null; export ANDROID_SERIAL="$1"; }

"$ADB" shell pm enable com.google.android.apps.tv.launcherx >/dev/null || true
"$ADB" shell pm enable com.google.android.tungsten.setupwraith >/dev/null || true
"$ADB" shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
"$ADB" shell input keyevent KEYCODE_HOME
echo "Home is now: $("$ADB" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')"
