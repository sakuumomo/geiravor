#!/usr/bin/env bash
# Pair Waydroid (phone) with Desktop Head Unit (dash).
# Requires: nix develop, Waydroid running, Android Auto installed in Waydroid,
# Head Unit Server started once from the Android Auto overflow menu.
set -euo pipefail

ADB="${ANDROID_HOME:?ANDROID_HOME is unset; run from nix develop}/platform-tools/adb"
DHU_BIN="${ANDROID_HOME}/extras/google/auto/desktop-head-unit"

if [[ ! -x "$DHU_BIN" && ! -f "$DHU_BIN" ]]; then
  echo "DHU binary missing at $DHU_BIN (flake extras-google-auto)." >&2
  exit 1
fi

IP="${WAYDROID_IP:-}"
if [[ -z "$IP" ]]; then
  IP="$(waydroid status 2>/dev/null | awk '/IP address/{print $3}' || true)"
fi
IP="${IP:-192.168.240.112}"

echo "adb connect ${IP}:5555"
"$ADB" connect "${IP}:5555" || true
"$ADB" devices
if "$ADB" devices | grep -q unauthorized; then
  echo "Waydroid adb is unauthorized. Accept the debugging prompt in the Waydroid window, then re-run." >&2
  exit 1
fi
"$ADB" forward tcp:5277 tcp:5277
echo "Forwarded tcp:5277. Start Head Unit Server in Android Auto if this is the first run."
echo "Launching DHU…"

if command -v desktop-head-unit >/dev/null 2>&1; then
  exec desktop-head-unit "$@"
fi
exec "$DHU_BIN" "$@"
