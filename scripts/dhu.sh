#!/usr/bin/env bash
# Pair Waydroid (phone) with Desktop Head Unit (dash).
# Requires: nix develop, Waydroid running, Android Auto installed in Waydroid,
# Head Unit Server started once from the Android Auto overflow menu.
#
# DHU 2.1 TLS uses a 2024 process clock (DHU_FAKETIME, baked into the FHS
# wrap) so Auto does not show communication error 14 (car cert expired).
# This script waits for the Head Unit Server, talks to Waydroid :5277
# directly, keeps stdin open, and prefers a user systemd unit so DHU
# survives the launching shell (bwrap --die-with-parent).
set -euo pipefail

ADB="${ANDROID_HOME:?ANDROID_HOME is unset; run from nix develop}/platform-tools/adb"
DHU_BIN="${ANDROID_HOME}/extras/google/auto/desktop-head-unit"
UNIT="geiravor-dhu"
RUNTIME="${XDG_RUNTIME_DIR:-/tmp}/geiravor-dhu"

usage() {
  echo "Usage: $0 [--stop] [--foreground] [desktop-head-unit args…]" >&2
  echo "  --stop         stop a systemd-run DHU unit" >&2
  echo "  --foreground   exec DHU in this shell (default: user unit if available)" >&2
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "--stop" ]]; then
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --user stop "${UNIT}.service" 2>/dev/null || true
  fi
  echo "Stopped ${UNIT}."
  exit 0
fi

FOREGROUND=0
if [[ "${1:-}" == "--foreground" ]]; then
  FOREGROUND=1
  shift
fi

if [[ ! -x "$DHU_BIN" && ! -f "$DHU_BIN" ]]; then
  echo "DHU binary missing at $DHU_BIN (flake extras-google-auto)." >&2
  exit 1
fi

DHU="$(command -v desktop-head-unit 2>/dev/null || true)"
DHU="${DHU:-$DHU_BIN}"

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

echo "Waiting for Head Unit Server on ${IP}:5277 (Android Auto → ⋮ → Start head unit server)…"
ok=0
for _ in $(seq 1 20); do
  if "$ADB" shell ss -ltn 2>/dev/null | grep -q ':5277'; then
    ok=1
    break
  fi
  sleep 1
done
if [[ "$ok" -ne 1 ]]; then
  echo "Head Unit Server is not listening on :5277. Start it from Android Auto, then re-run." >&2
  exit 1
fi
echo "Head Unit Server is up."

mkdir -p "$RUNTIME"
FIFO="$RUNTIME/stdin"
rm -f "$FIFO"
mkfifo "$FIFO"

# DHU exits on stdin EOF. O_RDWR on a fifo never EOFs.
run_dhu() {
  export DISPLAY="${DISPLAY:-:0}"
  export XAUTHORITY="${XAUTHORITY:-}"
  export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-}"
  export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
  export SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-x11}"
  exec 3<>"$FIFO"
  exec 0<&3
  echo "Launching DHU at ${IP}:5277…"
  exec "$DHU" -a "${IP}:5277" "$@"
}

have_user_systemd=0
if [[ "$FOREGROUND" -eq 0 ]] && command -v systemd-run >/dev/null 2>&1 && command -v systemctl >/dev/null 2>&1; then
  if systemctl --user show-environment >/dev/null 2>&1; then
    have_user_systemd=1
  fi
fi

if [[ "$have_user_systemd" -eq 1 ]]; then
  systemctl --user stop "${UNIT}.service" 2>/dev/null || true
  WRAP="$RUNTIME/run.sh"
  {
    echo '#!/usr/bin/env bash'
    echo 'set -euo pipefail'
    printf 'export DISPLAY=%q\n' "${DISPLAY:-:0}"
    printf 'export XAUTHORITY=%q\n' "${XAUTHORITY:-}"
    printf 'export WAYLAND_DISPLAY=%q\n' "${WAYLAND_DISPLAY:-}"
    printf 'export XDG_RUNTIME_DIR=%q\n' "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
    printf 'export SDL_VIDEODRIVER=%q\n' "${SDL_VIDEODRIVER:-x11}"
    printf 'export HOME=%q\n' "${HOME}"
    printf 'FIFO=%q\n' "$FIFO"
    printf 'DHU=%q\n' "$DHU"
    printf 'IP=%q\n' "$IP"
    echo 'exec 3<>"$FIFO"'
    echo 'exec 0<&3'
    echo 'echo "Launching DHU at ${IP}:5277…"'
    echo 'exec "$DHU" -a "${IP}:5277" "$@"'
  } >"$WRAP"
  chmod +x "$WRAP"
  systemd-run --user --unit="$UNIT" \
    --setenv=DISPLAY="${DISPLAY:-:0}" \
    --setenv=XAUTHORITY="${XAUTHORITY:-}" \
    --setenv=WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-}" \
    --setenv=XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" \
    --setenv=HOME="$HOME" \
    --setenv=SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-x11}" \
    "$WRAP" "$@"
  echo "DHU is ${UNIT}.service (survives this shell). Stop with: $0 --stop"
  exit 0
fi

run_dhu "$@"
