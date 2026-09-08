#!/usr/bin/env bash
# Desktop Head Unit against Waydroid (or another authorized adb device).
# Discovers the serial. Never installs to unauthorized. Does not kill :5277.
set -euo pipefail

pidfile="${XDG_RUNTIME_DIR:-/tmp}/geiravor-dhu.pid"

usage() {
  echo "usage: $0 [--stop] [serial]" >&2
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
    ip="$(waydroid --details-to-stdout status 2>/dev/null | sed -n 's/^IP address:[[:space:]]*//p' | tr -d '[:space:]')"
    if [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      adb connect "${ip}:5555" >/dev/null
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
  adb -s "$serial" forward tcp:5277 tcp:5277 >/dev/null || true
  for i in $(seq 1 60); do
    if timeout 1 bash -c 'echo >/dev/tcp/127.0.0.1/5277' 2>/dev/null; then
      echo "dhu.sh: Head Unit Server is on :5277"
      return 0
    fi
    sleep 1
  done
  echo "dhu.sh: Head Unit Server not listening on :5277 (start it on the phone; do not kill an existing one)" >&2
  return 1
}

stop_dhu() {
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

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "--stop" ]]; then
  stop_dhu
  exit 0
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

stop_dhu
desktop-head-unit &
echo $! >"$pidfile"
echo "dhu.sh: desktop-head-unit pid $(cat "$pidfile") serial=$serial"
wait
