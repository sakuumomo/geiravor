#!/usr/bin/env bash
# Desktop Head Unit against Waydroid (or another authorized adb device).
# Discovers the serial. Never installs to unauthorized. Does not kill :5277.
#
# Behaviour that worked on legacy/0.3:
# - talk to the device Head Unit Server on :5277 directly (not adb-forward localhost)
# - keep stdin open (DHU's console treats EOF as quit)
# - user systemd unit so bwrap --die-with-parent does not die with this shell
# Faketime stays in the nix FHS wrap (DHU_FAKETIME, default 2024-06-01).
set -euo pipefail

UNIT="geiravor-dhu"
RUNTIME="${XDG_RUNTIME_DIR:-/tmp}/geiravor-dhu"
pidfile="${XDG_RUNTIME_DIR:-/tmp}/geiravor-dhu.pid"

usage() {
  echo "usage: $0 [--stop] [--foreground] [serial]" >&2
}

discover_serial() {
  if [[ -n "${1:-}" ]]; then
    echo "$1"
    return
  fi
  local serial state
  while read -r serial state _; do
    [[ "$serial" == "List" || -z "$serial" ]] && continue
    if [[ "$state" == "unauthorized" ]]; then
      echo "dhu.sh: skip unauthorized $serial" >&2
      continue
    fi
    if [[ "$state" == "device" ]]; then
      echo "$serial"
      return
    fi
  done < <(adb devices)
  if command -v waydroid >/dev/null 2>&1; then
    local ip
    ip="$(waydroid status 2>/dev/null | awk '/IP address/{print $3}' || true)"
    if [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      adb connect "${ip}:5555" >/dev/null || true
      echo "${ip}:5555"
      return
    fi
  fi
  echo "dhu.sh: no authorized adb device" >&2
  exit 1
}

wait_head_unit_server() {
  local serial="$1"
  local i
  for i in $(seq 1 60); do
    if adb -s "$serial" shell ss -ltn 2>/dev/null | grep -q ':5277'; then
      echo "dhu.sh: Head Unit Server is on the device :5277"
      return 0
    fi
    sleep 1
  done
  echo "dhu.sh: Head Unit Server not listening on :5277 (start it on the phone; do not kill an existing one)" >&2
  return 1
}

stop_dhu() {
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --user stop "${UNIT}.service" 2>/dev/null || true
  fi
  if [[ -f "$pidfile" ]]; then
    local pid
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$pidfile"
  fi
  echo "dhu.sh: stopped desktop-head-unit (left :5277 alone)"
}

dhu_args_for_serial() {
  local serial="$1"
  # tcp serial (Waydroid ip:5555): DHU speaks GAL to device :5277 itself.
  # USB serial: official adb forward to localhost.
  if [[ "$serial" == *:* ]]; then
    local host="${serial%%:*}"
    echo "-a" "${host}:5277"
  else
    adb -s "$serial" forward tcp:5277 tcp:5277 >/dev/null || true
  fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "--stop" ]]; then
  stop_dhu
  exit 0
fi

FOREGROUND=0
if [[ "${1:-}" == "--foreground" ]]; then
  FOREGROUND=1
  shift
fi

serial="$(discover_serial "${1:-}")"
state="$(adb devices | awk -v s="$serial" '$1==s {print $2}')"
if [[ "$state" == "unauthorized" ]]; then
  echo "dhu.sh: refusing unauthorized $serial" >&2
  exit 1
fi

wait_head_unit_server "$serial"

if ! command -v desktop-head-unit >/dev/null 2>&1; then
  echo "dhu.sh: desktop-head-unit not on PATH (use nix develop)" >&2
  exit 1
fi

DHU="$(command -v desktop-head-unit)"
# shellcheck disable=SC2207
addr=($(dhu_args_for_serial "$serial"))

export DISPLAY="${DISPLAY:-:0}"
export SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-x11}"

mkdir -p "$RUNTIME"
FIFO="$RUNTIME/stdin"
rm -f "$FIFO"
mkfifo "$FIFO"

stop_dhu

run_dhu() {
  # DHU exits on stdin EOF. O_RDWR on a fifo never EOFs.
  exec 3<>"$FIFO"
  exec 0<&3
  echo "dhu.sh: desktop-head-unit serial=$serial ${addr[*]}"
  exec "$DHU" "${addr[@]}"
}

have_user_systemd=0
if [[ "$FOREGROUND" -eq 0 ]] && command -v systemd-run >/dev/null 2>&1 && command -v systemctl >/dev/null 2>&1; then
  if systemctl --user show-environment >/dev/null 2>&1; then
    have_user_systemd=1
  fi
fi

if [[ "$have_user_systemd" -eq 1 ]]; then
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
    printf 'ADDR=(%s)\n' "${addr[*]}"
    echo 'exec 3<>"$FIFO"'
    echo 'exec 0<&3'
    echo 'echo "dhu.sh: launching ${DHU} ${ADDR[*]}"'
    echo 'exec "$DHU" "${ADDR[@]}"'
  } >"$WRAP"
  chmod +x "$WRAP"
  systemd-run --user --unit="$UNIT" \
    --setenv=DISPLAY="${DISPLAY:-:0}" \
    --setenv=XAUTHORITY="${XAUTHORITY:-}" \
    --setenv=WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-}" \
    --setenv=XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" \
    --setenv=HOME="$HOME" \
    --setenv=SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-x11}" \
    "$WRAP"
  echo "dhu.sh: ${UNIT}.service (survives this shell). Stop with: $0 --stop"
  exit 0
fi

echo $$ >"$pidfile"
run_dhu
