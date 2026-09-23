#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# test-egress-guard.sh — end-to-end test of egress-guard.sh and
# probe-isolation.sh against real iptables and real containers.
#
# Run as root on a DISPOSABLE Linux Docker host: the host-guard CI job, or the
# local guard lab (docker:27-dind, see DEPLOY.md §9). It creates its own
# networks and containers under the compose project name "bvtest", and removes
# them and the guard's rules when it exits.
#
# Usage: bash scripts/host/test-egress-guard.sh
set -uo pipefail   # no -e: every assertion reports, the summary decides

HERE=$(cd "$(dirname "$0")" && pwd)
GUARD="$HERE/egress-guard.sh"
PROBE="$HERE/probe-isolation.sh"
PROJECT=bvtest
PROBE_IMAGE="${PROBE_IMAGE:-bash:5.2}"
PY_IMAGE="${PY_IMAGE:-python:3.12-alpine}"
PUBLIC_IP=1.1.1.1          # answers on 80 and 443; an IP, so no DNS dependency
HOST_PORT=18099            # host listener
NEIGHBOR_PORT=18098        # neighbor's published port
LAN_PORT=18097             # published port of a service on the project's lan network
PASSES=0
FAILS=0

pass() { PASSES=$((PASSES + 1)); echo "PASS  $*"; }
fail() { FAILS=$((FAILS + 1)); echo "FAIL  $*"; }
info() { echo "INFO  $*"; }

guard() { BV_PROJECTS=$PROJECT BV_GUARD_SKIP_LOCATION_CHECK=1 bash "$GUARD" "$@"; }

# tcp_from CONTAINER HOST PORT -> 0 when a TCP connection succeeds within 3 s
tcp_from() {
  docker exec "$1" bash -c 'timeout 3 bash -c "</dev/tcp/$0/$1"' "$2" "$3" >/dev/null 2>&1
}

expect_open() {
  if tcp_from "$@"; then pass "open    $1 -> $2:$3"; else fail "expected OPEN    $1 -> $2:$3"; fi
}
expect_blocked() {
  if tcp_from "$@"; then fail "expected BLOCKED $1 -> $2:$3"; else pass "blocked $1 -> $2:$3"; fi
}
# host_tcp HOST PORT -> 0 when the host itself can connect (inbound to a published port)
host_tcp() { timeout 3 bash -c "</dev/tcp/$1/$2" >/dev/null 2>&1; }
baseline() {
  if tcp_from "$@"; then info "baseline open    $1 -> $2:$3"; else info "baseline closed  $1 -> $2:$3"; fi
}

gateway_of() { docker network inspect -f '{{range .IPAM.Config}}{{if .Gateway}}{{.Gateway}} {{end}}{{end}}' "$1" | awk '{print $1}'; }
host_ip() { ip -4 route get "$PUBLIC_IP" | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}'; }
chains_v4() { for c in BV-GUARD BV-GUARD-IN BV-LOG-DROP; do iptables -w -S "$c" 2>/dev/null; done; }
docker_chains_v4() { for c in DOCKER DOCKER-ISOLATION-STAGE-1 DOCKER-ISOLATION-STAGE-2; do iptables -w -S "$c" 2>/dev/null; done; }

HOST_LISTENER_PID=""
cleanup() {
  guard remove >/dev/null 2>&1 || true
  docker rm -f bvt-int bvt-int-peer bvt-egress bvt-tunnel bvt-legacy bvt-v6 bvt-neighbor bvt-mount bvt-lan bvt-lan-probe >/dev/null 2>&1 || true
  for n in bvtest_internal bvtest_egress_out bvtest_tunnel_out bvtest_legacy bvtest_lan bvtest_v6 bvtest_neighbor; do
    docker network rm "$n" >/dev/null 2>&1 || true
  done
  [ -n "$HOST_LISTENER_PID" ] && kill "$HOST_LISTENER_PID" >/dev/null 2>&1 || true
  rm -rf /tmp/bvloc
}
trap cleanup EXIT

[ "$(id -u)" -eq 0 ] || { echo "run as root" >&2; exit 2; }
cleanup   # leftovers from an aborted run

# --- fixture --------------------------------------------------------------
label=(--label "com.docker.compose.project=$PROJECT")
docker network create "${label[@]}" --internal bvtest_internal >/dev/null
docker network create "${label[@]}" bvtest_egress_out >/dev/null
docker network create "${label[@]}" bvtest_tunnel_out >/dev/null
docker network create "${label[@]}" bvtest_legacy >/dev/null
docker network create "${label[@]}" bvtest_lan >/dev/null
docker network create bvtest_neighbor >/dev/null   # NOT part of the project
V6=0
# Only exercise IPv6 where Docker manages ip6tables; otherwise the guard would
# (correctly) refuse to run, and that refusal is not what this block tests.
if command -v ip6tables >/dev/null && ip6tables -w -S DOCKER-USER >/dev/null 2>&1 \
  && docker network create "${label[@]}" --ipv6 --subnet fd00:b7::/64 bvtest_v6 >/dev/null 2>&1; then
  V6=1
fi

docker run -d --name bvt-int     --network bvtest_internal   "$PROBE_IMAGE" sleep 3600 >/dev/null
docker run -d --name bvt-int-peer --network bvtest_internal  "$PY_IMAGE" python -m http.server 8000 >/dev/null
docker run -d --name bvt-egress  --network bvtest_egress_out "$PROBE_IMAGE" sleep 3600 >/dev/null
docker run -d --name bvt-tunnel  --network bvtest_tunnel_out "$PROBE_IMAGE" sleep 3600 >/dev/null
docker run -d --name bvt-legacy  --network bvtest_legacy     "$PROBE_IMAGE" sleep 3600 >/dev/null
docker run -d --name bvt-neighbor --network bvtest_neighbor -p "$NEIGHBOR_PORT:8000" "$PY_IMAGE" python -m http.server 8000 >/dev/null
docker run -d --name bvt-lan     --network bvtest_lan -p "$LAN_PORT:8000" "$PY_IMAGE" python -m http.server 8000 >/dev/null
docker run -d --name bvt-lan-probe --network bvtest_lan     "$PROBE_IMAGE" sleep 3600 >/dev/null
[ "$V6" -eq 1 ] && docker run -d --name bvt-v6 --network bvtest_v6 "$PROBE_IMAGE" sleep 3600 >/dev/null

python3 -m http.server "$HOST_PORT" --bind :: >/dev/null 2>&1 &
HOST_LISTENER_PID=$!
sleep 3

HOST=$(host_ip)
NEIGHBOR_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' bvt-neighbor)
PEER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' bvt-int-peer)
GW_LEGACY=$(gateway_of bvtest_legacy)
GW_EGRESS=$(gateway_of bvtest_egress_out)
GW_TUNNEL=$(gateway_of bvtest_tunnel_out)
GW_INT=$(gateway_of bvtest_internal)
info "host=$HOST neighbor=$NEIGHBOR_IP peer=$PEER_IP v6=$V6"

# --- baseline: prove the harness can see exposure --------------------------
expect_open bvt-legacy "$HOST" "$HOST_PORT"
expect_open bvt-legacy "$GW_LEGACY" "$HOST_PORT"
baseline bvt-legacy "$HOST" "$NEIGHBOR_PORT"
baseline bvt-legacy "$NEIGHBOR_IP" 8000
baseline bvt-int "$GW_INT" "$HOST_PORT"
[ "$V6" -eq 1 ] && baseline bvt-v6 "fd00:b7::1" "$HOST_PORT"
DOCKER_BEFORE=$(docker_chains_v4)

# --- roles -------------------------------------------------------------------
roles=$(guard roles)
for want in "bvtest_internal internal" "bvtest_egress_out egress" "bvtest_tunnel_out tunnel" "bvtest_legacy transitional" "bvtest_lan lan"; do
  if grep -qx "$want" <<<"$roles"; then pass "role: $want"; else fail "role missing: $want (got: $roles)"; fi
done

# --- apply -------------------------------------------------------------------
if guard apply; then pass "apply exit 0"; else fail "apply failed"; fi

for c in bvt-int bvt-egress bvt-tunnel bvt-legacy; do
  expect_blocked "$c" "$HOST" "$HOST_PORT"
  expect_blocked "$c" "$HOST" "$NEIGHBOR_PORT"
  expect_blocked "$c" "$NEIGHBOR_IP" 8000
done
expect_blocked bvt-legacy "$GW_LEGACY" "$HOST_PORT"
expect_blocked bvt-egress "$GW_EGRESS" "$HOST_PORT"
expect_blocked bvt-tunnel "$GW_TUNNEL" "$HOST_PORT"
expect_blocked bvt-int "$GW_INT" "$HOST_PORT"

expect_open    bvt-int "$PEER_IP" 8000              # same network still works
expect_blocked bvt-int "$PUBLIC_IP" 443             # internal: nothing leaves
expect_open    bvt-egress "$PUBLIC_IP" 443
expect_blocked bvt-egress "$PUBLIC_IP" 80           # egress network: 443 only
expect_open    bvt-tunnel "$PUBLIC_IP" 443
expect_blocked bvt-tunnel "$PUBLIC_IP" 80           # tunnel network: 443/7844 only
expect_open    bvt-legacy "$PUBLIC_IP" 443          # transitional: public, any port
expect_open    bvt-legacy "$PUBLIC_IP" 80
expect_blocked bvt-lan-probe "$PUBLIC_IP" 443       # lan: nothing new goes out...
expect_blocked bvt-lan-probe "$HOST" "$HOST_PORT"
if host_tcp "$HOST" "$LAN_PORT"; then               # ...but its published port still answers
  pass "open    host -> lan network's published port"
else
  fail "expected OPEN host -> lan network's published port $LAN_PORT"
fi

if iptables -w -S BV-GUARD | grep -q -- '--dports 443,7844'; then pass "tunnel allows tcp 443,7844"; else fail "tunnel tcp rule missing"; fi
if iptables -w -S BV-GUARD | grep -q -- '-p udp -m udp --dport 7844'; then pass "tunnel allows udp 7844"; else fail "tunnel udp rule missing"; fi

if [ "$V6" -eq 1 ]; then
  if ip6tables -w -S BV-GUARD | grep -q 'fd00:b7::/64'; then pass "IPv6 subnet guarded"; else fail "IPv6 subnet not in ip6tables BV-GUARD"; fi
  expect_blocked bvt-v6 "fd00:b7::1" "$HOST_PORT"
else
  info "SKIPPED IPv6 checks: this Docker host cannot create IPv6 networks"
fi

# --- probe-isolation.sh agrees -----------------------------------------------
if bash "$PROBE" --project "$PROJECT" --image "$PROBE_IMAGE" --target "$HOST:$HOST_PORT" --target "$HOST:$NEIGHBOR_PORT" --public "$PUBLIC_IP:443"; then
  pass "probe-isolation.sh: all expectations met"
else
  fail "probe-isolation.sh reported a failure"
fi

# --- idempotency ---------------------------------------------------------------
before=$(chains_v4)
out=$(guard apply)
after=$(chains_v4)
if [ "$before" = "$after" ]; then pass "second apply leaves rules identical"; else fail "second apply changed the rules"; fi
if grep -q 'nothing to change' <<<"$out"; then pass "second apply reports nothing to change"; else fail "second apply output: $out"; fi
if [ -z "$(guard apply --quiet)" ]; then pass "--quiet prints nothing when nothing changed"; else fail "--quiet printed on a clean run"; fi

# --- drift detection and repair ----------------------------------------------------
check_is() { guard check >/dev/null 2>&1; local rc=$?; if [ "$rc" -eq "$1" ]; then pass "check exit $1 ($2)"; else fail "check exit $rc, expected $1 ($2)"; fi; }
check_is 0 "clean"
iptables -w -D BV-GUARD 3
check_is 1 "rule deleted"
if guard apply --quiet | grep -q '^egress-guard: repaired'; then pass "apply --quiet reports the repair"; else fail "repair not reported"; fi
check_is 0 "after repair"
iptables -w -D DOCKER-USER -j BV-GUARD
check_is 1 "jump deleted"
guard apply --quiet >/dev/null
check_is 0 "jump restored"
iptables -w -I DOCKER-USER 1 -j RETURN
check_is 1 "jump not first"
guard apply --quiet >/dev/null
check_is 0 "jump first again"
iptables -w -D DOCKER-USER -j RETURN 2>/dev/null || true

# --- location safety ------------------------------------------------------------------
mkdir -p /tmp/bvloc && cp "$GUARD" /tmp/bvloc/egress-guard.sh
chown -R root:root /tmp/bvloc && chmod 700 /tmp/bvloc && chmod 700 /tmp/bvloc/egress-guard.sh
docker run -d --name bvt-mount -v /tmp/bvloc:/x:ro "$PROBE_IMAGE" sleep 3600 >/dev/null
BV_PROJECTS=$PROJECT bash /tmp/bvloc/egress-guard.sh check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'mounted into a container' /tmp/bvloc.out; then pass "refuses to run from a container-mounted directory"; else fail "location check: rc=$rc $(cat /tmp/bvloc.out)"; fi
docker rm -f bvt-mount >/dev/null
chmod 775 /tmp/bvloc
BV_PROJECTS=$PROJECT bash /tmp/bvloc/egress-guard.sh check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'writable' /tmp/bvloc.out; then pass "refuses a group-writable directory"; else fail "permission check: rc=$rc $(cat /tmp/bvloc.out)"; fi
chmod 700 /tmp/bvloc
BV_PROJECTS=$PROJECT bash /tmp/bvloc/egress-guard.sh check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 0 ]; then pass "safe location accepted"; else fail "safe location rejected: rc=$rc $(cat /tmp/bvloc.out)"; fi
rm -f /tmp/bvloc.out

# --- remove ---------------------------------------------------------------------------
guard remove >/dev/null
if ! iptables -w -S | grep -q 'BV-'; then pass "remove: no BV- rules left (IPv4)"; else fail "remove left IPv4 rules"; fi
if command -v ip6tables >/dev/null && ip6tables -w -S 2>/dev/null | grep -q 'BV-'; then fail "remove left IPv6 rules"; else pass "remove: no BV- rules left (IPv6)"; fi
if [ "$(docker_chains_v4)" = "$DOCKER_BEFORE" ]; then pass "Docker's own chains untouched"; else fail "Docker's chains changed"; fi

echo
echo "passes=$PASSES fails=$FAILS"
[ "$FAILS" -eq 0 ]
