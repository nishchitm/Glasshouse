#!/usr/bin/env bash
# Turns off preinstalled TV apps you don't need. Apps are disabled, never uninstalled, so --undo brings them all back.
#
# Usage: scripts/debloat.sh [<tv-ip>:5555] [--apply] [--extras] [--undo]
#   (no flags)  Preview only: shows what would be disabled and changes nothing.
#   --apply     Disable them, and note each one in debloat-disabled.txt for --undo.
#   --extras    Also include the untested list below. Could affect a feature you use.
#   --undo      Re-enable everything in debloat-disabled.txt.
#
# Only apps found on your TV are touched. Edit the lists to suit: delete a line to keep that app.
set -euo pipefail
ADB="$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")"
LOG="$(cd "$(dirname "$0")/.." && pwd)/debloat-disabled.txt"

# Run disabled on a Sony Bravia (Android TV 12) with no problems found.
TESTED="
com.sony.dtv.all4launcher
com.sony.dtv.ardlauncher
com.sony.dtv.iplayerlauncher
com.sony.dtv.itvlauncher
com.sony.dtv.my5launcher
com.sony.dtv.soundslauncher
com.sony.dtv.b2b.softap
com.sony.dtv.b2b.vendorprotocol
com.youview.poa
de.hdplus.tvapp.androidtv
com.zdf.android.mediathek
de.swr.avp.ard.tv
com.disney.disneyplus
com.mxtech.videoplayer.television
"

# Promos, viewing data and preinstalled extras. These look safe to disable, but haven't been tested.
EXTRAS="
tv.samba.ssm
tv.samba.ssm.ui
com.sony.dtv.promos
com.sony.dtv.sonyselect
com.sony.dtv.recapp
com.sony.dtv.livingfit
com.sonypicturescore
com.vewd.core.integration.dia
android.autoinstalls.config.sony.bravia
"

APPLY=0; WITH_EXTRAS=0; UNDO=0
for a in "$@"; do
  case "$a" in
    --apply) APPLY=1 ;;
    --extras) WITH_EXTRAS=1 ;;
    --undo) UNDO=1 ;;
    *) "$ADB" connect "$a" >/dev/null; export ANDROID_SERIAL="$a" ;;
  esac
done

if [[ $UNDO == 1 ]]; then
  [[ -f "$LOG" ]] || { echo "Nothing to undo: $LOG not found."; exit 0; }
  while read -r pkg; do
    [[ -n "$pkg" ]] && "$ADB" shell pm enable "$pkg" >/dev/null && echo "Re-enabled $pkg"
  done < "$LOG"
  rm -f "$LOG"
  exit 0
fi

installed="$("$ADB" shell pm list packages | tr -d '\r' | sed 's/package://')"
disabled="$("$ADB" shell pm list packages -d | tr -d '\r' | sed 's/package://')"
list="$TESTED"
[[ $WITH_EXTRAS == 1 ]] && list="$list $EXTRAS"
count=0
for pkg in $list; do
  grep -qx "$pkg" <<<"$installed" || continue
  if grep -qx "$pkg" <<<"$disabled"; then
    echo "already off  $pkg"
    continue
  fi
  count=$((count + 1))
  if [[ $APPLY == 1 ]]; then
    "$ADB" shell pm disable-user --user 0 "$pkg" >/dev/null
    echo "$pkg" >> "$LOG"
    echo "disabled     $pkg"
  else
    echo "would disable $pkg"
  fi
done
if [[ $APPLY == 1 ]]; then
  echo "$count disabled. Undo with: scripts/debloat.sh --undo"
else
  echo "$count to disable. Nothing changed: add --apply to do it, --extras for the untested list."
fi
