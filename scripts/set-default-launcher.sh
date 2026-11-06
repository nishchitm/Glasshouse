#!/usr/bin/env bash
# Makes Glasshouse the TV's home screen (the Home button and boot). Undo with scripts/restore-google-tv.sh.
#
# Usage: scripts/set-default-launcher.sh [<tv-ip>:5555] [--disable-google-tv]
#   --disable-google-tv  also turns off the Google TV home app, for firmware that ignores the default
#                        and keeps returning there. It is disabled, not removed, so restoring brings it back.
set -euo pipefail
ADB="$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")"
DISABLE=0
for a in "$@"; do
  case "$a" in
    --disable-google-tv) DISABLE=1 ;;
    *) "$ADB" connect "$a" >/dev/null; export ANDROID_SERIAL="$a" ;;
  esac
done

"$ADB" shell cmd package set-home-activity com.cleantv.launcher/.HomeAlias
if [[ $DISABLE == 1 ]]; then
  "$ADB" shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
  # With Google TV's home off, its setup app offers a "recovery" home that outranks any default; turn it off
  # too. On one test TV this made the TV forget its saved Wi-Fi once; re-adding it fixed it.
  "$ADB" shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
  "$ADB" shell cmd package set-home-activity com.cleantv.launcher/.HomeAlias
fi
echo "Home is now: $("$ADB" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')"
