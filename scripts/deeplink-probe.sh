#!/usr/bin/env bash
# Checks, on the real TV, which streaming apps open straight onto a title from the links Glasshouse sends.
#
# For each service it force-stops the app, sends the same kind of link Glasshouse would, and asks what you
# see on the TV. Results go to docs/deeplink-matrix.md, so the link map in AppLauncher/TitleLinks can be
# tuned from what actually works on your TV.
#
# Usage:  scripts/deeplink-probe.sh            (uses the only connected device)
#         scripts/deeplink-probe.sh <tv-ip>:5555
set -euo pipefail

ADB="$(command -v adb || echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb")"
[[ $# -ge 1 ]] && { "$ADB" connect "$1" >/dev/null; export ANDROID_SERIAL="$1"; }
OUT="$(cd "$(dirname "$0")/.." && pwd)/docs/deeplink-matrix.md"

# service | package | title | link (title page, or the app's search)
TESTS=(
  "Netflix|com.netflix.ninja|Stranger Things (title page)|https://www.netflix.com/title/80057281"
  "Prime Video|com.amazon.amazonvideo.livingroom|House (title page)|https://www.primevideo.com/detail/0QSL3DL67B6SWM2VQ50ZHUX7HN"
  "YouTube|com.google.android.youtube.tv|F1 highlights (search)|https://www.youtube.com/results?search_query=F1%20highlights"
  "JioHotstar|in.startv.hotstar|Game of Thrones (title page)|https://www.hotstar.com/1971002880"
  "SonyLIV|com.sonyliv|CID (title page)|https://www.sonyliv.com/shows/1700000196"
  "Crunchyroll|com.crunchyroll.crunchyroid|Frieren (title page)|https://www.crunchyroll.com/series/GG5H5XQX4"
  "Apple TV+|com.apple.atve.sony.appletv|Severance (title page)|https://tv.apple.com/show/umc.cmc.1srk2goyh2q2zdxcx605w8vtx"
)

installed="$("$ADB" shell pm list packages | tr -d '\r')"
{
  echo "# Deep-link results on $("$ADB" shell getprop ro.product.model | tr -d '\r') ($(date +%Y-%m-%d))"
  echo
  echo "| Service | Test | Result |"
  echo "|---|---|---|"
} > "$OUT"

for t in "${TESTS[@]}"; do
  IFS='|' read -r service pkg title url <<< "$t"
  if ! grep -q "package:$pkg$" <<< "$installed"; then
    echo "-- $service: $pkg not installed, skipping"
    echo "| $service | $title | not installed |" >> "$OUT"
    continue
  fi
  echo
  echo "== $service: $title"
  "$ADB" shell am force-stop "$pkg"
  extra=()
  [[ "$service" == "Netflix" ]] && extra=(--es source 30)
  # `am start` exits 0 even when nothing handles the link, so check what it printed.
  started="$("$ADB" shell am start -a android.intent.action.VIEW -d "'$url'" -p "$pkg" ${extra[@]+"${extra[@]}"} 2>&1 || true)"
  grep -q "Error" <<< "$started" && echo "   (the app doesn't accept this link)"
  read -r -p "   On the TV: [t]itle page, [s]earch results, [h]ome screen, [n]othing/error? " ans
  case "$ans" in
    t) result="✅ opens the title" ;;
    s) result="🔎 opens search" ;;
    h) result="🏠 app home only" ;;
    *) result="❌ nothing / error" ;;
  esac
  echo "| $service | $title | $result |" >> "$OUT"
done

"$ADB" shell am start -n com.cleantv.launcher/.MainActivity >/dev/null 2>&1 || true
echo
echo "Saved to $OUT"
