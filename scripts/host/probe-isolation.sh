#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# probe-isolation.sh — show what a compose project's containers can reach.
#
# Usage:
#   probe-isolation.sh [--project NAME] [--target HOST:PORT]... [--public HOST:PORT] [--image IMAGE]
#
# For every network of the project, a throwaway container attached to that
# network alone tries each target. Each target is first tried from the host
# itself; if the host cannot reach it either, the row reads N/A (nothing is
# listening), so "blocked" always means something blocked it.
#
# Targets tried on every network, in addition to --target:
#   the network's own gateway address on ports 22 80 443 445 2049 2375 5001
# --public (default 1.1.1.1:443) must be reachable from egress, tunnel and
# transitional networks, and unreachable from internal and lan ones.
#
# Exit: 0 every expectation held · 1 at least one did not · 2 usage error.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
PROJECT=bottlevault
IMAGE=bash:5.2
PUBLIC=1.1.1.1:443
TARGETS=()
GW_PORTS="22 80 443 445 2049 2375 5001"
FAILS=0
PROBES=()

while [ $# -gt 0 ]; do
  case "$1" in
    --project) PROJECT=${2:?}; shift ;;
    --target) TARGETS+=("${2:?}"); shift ;;
    --public) PUBLIC=${2:?}; shift ;;
    --image) IMAGE=${2:?}; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

cleanup() { if [ ${#PROBES[@]} -gt 0 ]; then docker rm -f "${PROBES[@]}" >/dev/null 2>&1 || true; fi; }
trap cleanup EXIT

split_host() { printf '%s\n' "${1%:*}"; }
split_port() { printf '%s\n' "${1##*:}"; }

host_can_reach() { timeout 3 bash -c "</dev/tcp/$1/$2" >/dev/null 2>&1 </dev/null; }

# One long-lived probe per network, with --init. Without it the probe command
# would be the container's PID 1, which ignores timeout's SIGTERM, and every
# blocked probe would hang for the kernel's full TCP connect timeout (~2 min).
start_probe() { docker run -d --rm --init --network "$1" "$IMAGE" sleep 900 </dev/null; }

container_can_reach() { # PROBE HOST PORT
  docker exec "$1" bash -c 'timeout 3 bash -c "</dev/tcp/$0/$1"' "$2" "$3" >/dev/null 2>&1 </dev/null
}

row() { printf '%-28s %-13s %-30s %-8s %-9s %s\n' "$@"; }

expect_blocked() { # NET ROLE PROBE HOST PORT
  local net=$1 role=$2 probe=$3 host=$4 port=$5 from_container
  if ! host_can_reach "$host" "$port"; then
    row "$net" "$role" "$host:$port" closed - "N/A"
    return
  fi
  if container_can_reach "$probe" "$host" "$port"; then from_container=open; else from_container=blocked; fi
  if [ "$from_container" = blocked ]; then
    row "$net" "$role" "$host:$port" open blocked PASS
  else
    row "$net" "$role" "$host:$port" open OPEN FAIL
    FAILS=$((FAILS + 1))
  fi
}

expect_public() { # NET ROLE PROBE
  local net=$1 role=$2 probe=$3 host port want got
  host=$(split_host "$PUBLIC")
  port=$(split_port "$PUBLIC")
  case "$role" in internal|lan) want=blocked ;; *) want=open ;; esac
  if container_can_reach "$probe" "$host" "$port"; then got=open; else got=blocked; fi
  if [ "$got" = "$want" ]; then
    row "$net" "$role" "$PUBLIC (public)" - "$got" PASS
  else
    row "$net" "$role" "$PUBLIC (public)" - "$got" "FAIL (want $want)"
    FAILS=$((FAILS + 1))
  fi
}

roles=$(BV_PROJECTS="$PROJECT" bash "$HERE/egress-guard.sh" roles 2>/dev/null) \
  || { echo "could not list networks for project $PROJECT" >&2; exit 2; }
[ -n "$roles" ] || { echo "project $PROJECT has no networks (is the stack running?)" >&2; exit 2; }

row NETWORK ROLE TARGET HOST CONTAINER RESULT
while read -r net role; do
  probe=$(start_probe "$net")
  PROBES+=("$probe")
  for gw in $(docker network inspect -f '{{range .IPAM.Config}}{{.Gateway}} {{end}}' "$net"); do
    for port in $GW_PORTS; do expect_blocked "$net" "$role" "$probe" "$gw" "$port"; done
  done
  for t in "${TARGETS[@]}"; do
    expect_blocked "$net" "$role" "$probe" "$(split_host "$t")" "$(split_port "$t")"
  done
  expect_public "$net" "$role" "$probe"
done <<<"$roles"

echo
if [ "$FAILS" -eq 0 ]; then echo "all expectations held"; else echo "$FAILS expectation(s) failed"; fi
[ "$FAILS" -eq 0 ]
