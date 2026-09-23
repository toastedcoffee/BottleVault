#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# probe-isolation.sh — show what a compose project's containers can reach.
#
# Usage:
#   probe-isolation.sh [--project NAME] [--target HOST:PORT]... [--public HOST:PORT] [--image IMAGE]
#
# HOST is an IPv4 address or a bracketed IPv6 one ([fd00::1]:443). Names are
# refused: inside an internal network a name lookup times out, which would read
# as "blocked" without any connection ever being tried.
#
# For every network of the project, a throwaway container attached to that
# network alone tries each target, and the host tries it too. A row reads:
#   PASS   the host reached the target and the container did not
#   FAIL   the container reached a target it must not (even one the host
#          cannot), or could not reach the public target it must
#   N/A    neither reached it: nothing was proven, and the summary counts these
#   ERROR  the probe itself failed (no verdict from the container or host)
# "blocked" means the connection was refused, unreachable, or timed out after
# 3 s; any other outcome is an ERROR, never "blocked".
#
# Targets tried on every network, in addition to --target:
#   the network's own gateway address on ports 22 80 443 445 2049 2375 5001
#   (Docker records no gateway for IPv6 subnets; the probe then uses Docker's
#   default, the subnet's first address, and marks the row "(derived)")
# --public (default 1.1.1.1:443) must be reachable from egress, tunnel and
# transitional networks, and unreachable from internal and lan ones. If the host
# itself cannot reach it, those rows read N/A and the run exits 2.
#
# Exit: 0 every expectation held · 1 at least one did not (wins over 2)
#       2 usage or tool error: bad arguments, Docker or probe failures, ERROR
#         rows, or a --public the host cannot reach (nothing proven about it)
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
PROJECT=bottlevault
IMAGE=bash:5.2
PUBLIC=1.1.1.1:443
TARGETS=()
GW_PORTS="22 80 443 445 2049 2375 5001"
PASSES=0
FAILS=0
NAS=0
ERRORS=0
PROBES=()
DELIBERATE=0   # set just before every intended exit; anything else is a tool error

usage() {
  echo "usage: probe-isolation.sh [--project NAME] [--target HOST:PORT]... [--public HOST:PORT] [--image IMAGE]" >&2
}
die() { echo "probe-isolation.sh: $*" >&2; DELIBERATE=1; exit 2; }

# An unexpected failure (set -e) must still exit 2, never 1, which would read as
# "an expectation did not hold".
cleanup() {
  local rc=$?
  if [ ${#PROBES[@]} -gt 0 ]; then docker rm -f "${PROBES[@]}" >/dev/null 2>&1 </dev/null || true; fi
  if [ "$DELIBERATE" -eq 0 ]; then
    echo "probe-isolation.sh: stopped by an unexpected error (status $rc)" >&2
    exit 2
  fi
}
trap cleanup EXIT

while [ $# -gt 0 ]; do
  case "$1" in
    --project|--target|--public|--image)
      [ $# -ge 2 ] && [ -n "$2" ] || { usage; die "$1 needs a value"; }
      case "$1" in
        --project) PROJECT=$2 ;;
        --target) TARGETS+=("$2") ;;
        --public) PUBLIC=$2 ;;
        --image) IMAGE=$2 ;;
      esac
      shift ;;
    *) usage; die "unknown option: $1" ;;
  esac
  shift
done

# parse_target HOST:PORT: sets T_ADDR (for /dev/tcp) and T_PORT, or returns 1.
parse_target() {
  local addr port o
  case "$1" in
    \[*\]:*) addr=${1#\[}; addr=${addr%%\]*}; port=${1##*\]:}
             [[ $addr == *:* && $addr =~ ^[0-9A-Fa-f:.]+$ ]] || return 1 ;;
    *:*)     addr=${1%:*}; port=${1##*:}
             [[ $addr =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || return 1
             for o in ${addr//./ }; do [ $((10#$o)) -le 255 ] || return 1; done ;;
    *)       return 1 ;;
  esac
  [[ $port =~ ^[0-9]{1,5}$ ]] && [ $((10#$port)) -ge 1 ] && [ $((10#$port)) -le 65535 ] || return 1
  T_ADDR=$addr
  T_PORT=$((10#$port))
}
for t in "${TARGETS[@]}" "$PUBLIC"; do
  parse_target "$t" \
    || die "bad target '$t': want IPV4:PORT or [IPV6]:PORT with PORT 1-65535 (names are not accepted)"
done

# label ADDR PORT: how a target is written in the table.
label() { case "$1" in *:*) printf '[%s]:%s\n' "$1" "$2" ;; *) printf '%s:%s\n' "$1" "$2" ;; esac; }

# The connection test, run by bash on the host and inside each probe. It prints
# its own status after a sentinel, so a probe that never ran (dead container,
# no bash, daemon error) cannot be mistaken for a blocked connection.
# The 3 s limit treats a slow but reachable target as blocked; for LAN and
# gateway targets, which answer in milliseconds, that is acceptable.
TCP_TEST='timeout 3 bash -c "</dev/tcp/$0/$1" 2>&1; echo "bv-probe-rc=$?"'

# verdict OUTPUT -> open | blocked | error
# Measured: bash exits 1 on a refused or unreachable connect and prints why;
# GNU timeout exits 124 and busybox timeout 143 when the 3 s run out. Exit 1
# also covers a failed lookup or a bad port, so its message decides.
verdict() {
  local rc
  rc=$(printf '%s\n' "$1" | sed -n 's/^bv-probe-rc=\([0-9][0-9]*\)$/\1/p' | tail -n 1)
  case "$rc" in
    0) echo open ;;
    124|143) echo blocked ;;
    1) case "$1" in
         *"Connection refused"*|*"Network unreachable"*|*"Network is unreachable"*|\
         *"No route to host"*|*"Host is unreachable"*|*"Connection timed out"*) echo blocked ;;
         *) echo error ;;
       esac ;;
    *) echo error ;;
  esac
}

host_verdict() { verdict "$(bash -c "$TCP_TEST" "$1" "$2" 2>&1 </dev/null || true)"; }
container_verdict() { # PROBE ADDR PORT
  verdict "$(docker exec "$1" bash -c "$TCP_TEST" "$2" "$3" 2>&1 </dev/null || true)"
}

# One long-lived probe per network. --init only makes `sleep 900` answer the
# SIGTERM from `docker rm -f` at once; each test is a `docker exec`, never PID 1.
start_probe() { docker run -d --rm --init --network "$1" "$IMAGE" sleep 900 </dev/null; }

# probe_ready PROBE: the container runs and has bash and timeout.
probe_ready() {
  [ "$(docker exec "$1" bash -c 'command -v timeout >/dev/null && echo bv-probe-ready' 2>&1 </dev/null || true)" = bv-probe-ready ]
}

row() { printf '%-28s %-13s %-30s %-8s %-9s %s\n' "$@"; }
pass_row() { row "$@" PASS; PASSES=$((PASSES + 1)); }
fail_row() { local r=$6; row "$1" "$2" "$3" "$4" "$5" "$r"; FAILS=$((FAILS + 1)); }
na_row() { row "$@" "N/A (host cannot reach)"; NAS=$((NAS + 1)); }
error_row() { row "$@" ERROR; ERRORS=$((ERRORS + 1)); }

# first_host SUBNET: Docker's default gateway, the subnet's first host address.
# Host bits of a subnet address are zero, so adding 1 to the last group never
# carries.
first_host() {
  local addr=${1%/*} len=${1#*/} a b c d
  case "$addr" in
    *:*) [ "$len" -lt 128 ] || return 1
         case "$addr" in
           *::) printf '%s1\n' "$addr" ;;
           *) printf '%s:%x\n' "${addr%:*}" $((16#${addr##*:} + 1)) ;;
         esac ;;
    *)   [ "$len" -lt 31 ] || return 1
         IFS=. read -r a b c d <<<"$addr"
         printf '%s.%s.%s.%s\n' "$a" "$b" "$c" $((d + 1)) ;;
  esac
}

expect_blocked() { # NET ROLE PROBE ADDR PORT [NOTE]
  local net=$1 role=$2 probe=$3 addr=$4 port=$5 name host ctr
  name="$(label "$addr" "$port")${6:+ $6}"
  host=$(host_verdict "$addr" "$port")
  ctr=$(container_verdict "$probe" "$addr" "$port")
  [ "$host" = blocked ] && host=closed
  if [ "$host" = error ] || [ "$ctr" = error ]; then error_row "$net" "$role" "$name" "$host" "$ctr"
  elif [ "$ctr" = open ]; then fail_row "$net" "$role" "$name" "$host" OPEN FAIL
  elif [ "$host" = closed ]; then na_row "$net" "$role" "$name" "$host" "$ctr"
  else pass_row "$net" "$role" "$name" "$host" "$ctr"
  fi
}

expect_public() { # NET ROLE PROBE
  local net=$1 role=$2 probe=$3 want got
  case "$role" in internal|lan) want=blocked ;; *) want=open ;; esac
  got=$(container_verdict "$probe" "$PUB_ADDR" "$PUB_PORT")
  if [ "$got" = error ]; then error_row "$net" "$role" "$PUBLIC (public)" "$PUB_HOST" "$got"
  elif [ "$got" = open ] && [ "$want" = blocked ]; then fail_row "$net" "$role" "$PUBLIC (public)" "$PUB_HOST" OPEN "FAIL (want blocked)"
  elif [ "$PUB_HOST" = closed ]; then na_row "$net" "$role" "$PUBLIC (public)" "$PUB_HOST" "$got"
  elif [ "$got" = "$want" ]; then pass_row "$net" "$role" "$PUBLIC (public)" "$PUB_HOST" "$got"
  else fail_row "$net" "$role" "$PUBLIC (public)" "$PUB_HOST" "$got" "FAIL (want open)"
  fi
}

command -v timeout >/dev/null || die "timeout(1) not found on this host"
parse_target "$PUBLIC"
PUB_ADDR=$T_ADDR PUB_PORT=$T_PORT
PUB_HOST=$(host_verdict "$PUB_ADDR" "$PUB_PORT")
case "$PUB_HOST" in
  open) ;;
  blocked) PUB_HOST=closed ;;
  *) die "the host could not test $PUBLIC (bash /dev/tcp or timeout failed)" ;;
esac

# The guard's stderr passes through: its WARN lines, and its reason on failure.
roles=$(BV_PROJECTS="$PROJECT" bash "$HERE/egress-guard.sh" roles </dev/null) \
  || die "egress-guard.sh roles failed; could not list networks for project $PROJECT"
[ -n "$roles" ] || die "project $PROJECT has no networks (is the stack running?)"

row NETWORK ROLE TARGET HOST CONTAINER RESULT
while read -r net role; do
  probe=$(start_probe "$net") || die "could not start a probe container ($IMAGE) on $net"
  PROBES+=("$probe")
  probe_ready "$probe" || die "the probe on $net cannot run the test: $IMAGE needs bash and timeout"
  subnets=$(docker network inspect -f '{{range .IPAM.Config}}{{.Subnet}} {{with index . "Gateway"}}{{.}}{{end}}{{"\n"}}{{end}}' "$net" </dev/null) \
    || die "could not inspect network $net"
  while read -r subnet gw; do
    [ -n "$subnet" ] || continue
    note=""
    if [ -z "$gw" ]; then
      note="(derived)"
      if ! gw=$(first_host "$subnet"); then
        error_row "$net" "$role" "gateway of $subnet" - -
        continue
      fi
    fi
    for port in $GW_PORTS; do expect_blocked "$net" "$role" "$probe" "$gw" "$port" "$note"; done
  done <<<"$subnets"
  for t in "${TARGETS[@]}"; do
    parse_target "$t"
    expect_blocked "$net" "$role" "$probe" "$T_ADDR" "$T_PORT"
  done
  expect_public "$net" "$role" "$probe"
done <<<"$roles"

echo
echo "rows: $PASSES passed, $FAILS failed, $NAS N/A, $ERRORS error"
echo "N/A rows: $NAS (neither the host nor the probe reached the target; they prove nothing)"
[ "$PUB_HOST" = open ] \
  || echo "public target $PUBLIC is unreachable from this host: internet isolation was NOT proven; check --public and rerun"
[ "$ERRORS" -eq 0 ] || echo "$ERRORS row(s) could not be probed (ERROR)"
DELIBERATE=1
if [ "$FAILS" -gt 0 ]; then echo "$FAILS expectation(s) failed"; exit 1; fi
if [ "$ERRORS" -gt 0 ] || [ "$PUB_HOST" != open ]; then echo "incomplete: not every expectation was proven"; exit 2; fi
echo "all expectations held"
exit 0
