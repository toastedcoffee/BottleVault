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
#   BV_REQUIRE_FULL=1  a check that cannot run on this host (no IPv6, forged
#                      packets or LAN stand-in unreachable even before the guard,
#                      a legacy iptables table already present) is a FAIL, not a
#                      SKIPPED line. CI sets it, so coverage cannot silently shrink.
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
ONLINK_PORT=18094         # listener on the stand-in LAN host's public-range address
OTHER_DNS=9.9.9.9          # a public resolver the guard is NOT told to allow
REQUIRE_FULL=${BV_REQUIRE_FULL:-0}
PASSES=0
FAILS=0

pass() { PASSES=$((PASSES + 1)); echo "PASS  $*"; }
fail() { FAILS=$((FAILS + 1)); echo "FAIL  $*"; }
info() { echo "INFO  $*"; }
skip() { if [ "$REQUIRE_FULL" = 1 ]; then fail "not run (BV_REQUIRE_FULL=1): $*"; else info "SKIPPED $*"; fi; }

# GUARD_DNS is the allowed-DNS list every guard run gets; set below to the
# resolver Docker really forwards to, and changed by the DNS checks.
GUARD_DNS=""
guard() { BV_PROJECTS=$PROJECT BV_ALLOWED_DNS=$GUARD_DNS BV_GUARD_SKIP_LOCATION_CHECK=1 bash "$GUARD" "$@"; }
# The location checks run a copy of the guard with its location check on.
LOC_GUARD=/tmp/bvloc/egress-guard.sh
guard_loc() { BV_PROJECTS=$PROJECT BV_ALLOWED_DNS=$GUARD_DNS bash "$LOC_GUARD" "$@"; }

# lookup_from CONTAINER NAME [SERVER] -> 0 when NAME resolves: through Docker's
# embedded resolver (as every service does), or by asking SERVER directly.
lookup_from() {
  if [ $# -ge 3 ]; then
    docker exec "$1" timeout 8 nslookup -timeout=2 -retry=1 "$2" "$3" >/dev/null 2>&1
  else
    docker exec "$1" timeout 12 getent hosts "$2" >/dev/null 2>&1
  fi
}
expect_resolves() {
  if lookup_from "$@"; then pass "resolves  $1 -> $2${3:+ @$3}"; else fail "expected RESOLVES  $1 -> $2${3:+ @$3}"; fi
}
expect_unresolved() {
  if lookup_from "$@"; then fail "expected NO LOOKUP $1 -> $2${3:+ @$3}"; else pass "no lookup $1 -> $2${3:+ @$3}"; fi
}

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
# Everything the guard owns or touches, both families, jumps and scratch chains
# included (a leaked *-NEW chain is a change too).
guard_state() {
  local t c
  for t in iptables ip6tables; do
    command -v "$t" >/dev/null || continue
    for c in DOCKER-USER FORWARD INPUT BV-GUARD BV-GUARD-IN BV-LOG-DROP BV-GUARD-NEW BV-GUARD-IN-NEW BV-LOG-DROP-NEW; do
      "$t" -w -S "$c" 2>/dev/null
    done
  done
}
LOCK=/run/bv-egress-guard.lock

# --- fault injection -------------------------------------------------------
# A shim directory placed first in PATH for ONE guard invocation replaces a
# single tool with a wrapper that fails in one specific situation and otherwise
# runs the real tool. Absolute paths are resolved now, before any shim exists.
SHIMS=/tmp/bvshim
REAL_DOCKER=$(command -v docker)
REAL_IPT=$(command -v iptables)
REAL_IPTR=$(command -v iptables-restore)
LOCK_HOLDER_PID=""

# shim NAME TOOL REAL CONDITION: $SHIMS/NAME/TOOL fails when the bash test
# CONDITION (over the tool's "$@") holds, and otherwise execs REAL.
shim() {
  mkdir -p "$SHIMS/$1"
  printf '#!/usr/bin/env bash\nif %s; then echo "shim: injected %s failure" >&2; exit 1; fi\nexec %s "$@"\n' \
    "$4" "$2" "$3" >"$SHIMS/$1/$2"
  chmod 755 "$SHIMS/$1/$2"
}

# fill_fault NAME CHAIN: make the fill of CHAIN fail part-way, after its third
# rule, whichever way the guard fills it. Rule by rule: the iptables shim
# rejects that append. As one iptables-restore transaction: the shim hands the
# REAL iptables-restore the same input with an invalid rule spliced in after
# the third rule, so the real tool fails in the middle of its input.
fill_fault() {
  local chain=$2 dir="$SHIMS/$1"
  mkdir -p "$dir"
  cat >"$dir/iptables" <<EOF
#!/usr/bin/env bash
if [ "\$2" = -A ] && [ "\$3" = $chain ]; then
  n=\$(( \$(cat $dir/appends 2>/dev/null || echo 0) + 1 ))
  echo "\$n" >$dir/appends
  if [ "\$n" -gt 3 ]; then echo "shim: injected iptables failure" >&2; exit 1; fi
fi
exec $REAL_IPT "\$@"
EOF
  chmod 755 "$dir/iptables"
  cat >"$dir/iptables-restore" <<EOF
#!/usr/bin/env bash
awk '{ print } /^-A $chain / { n++; if (n == 3) print "-A $chain -m bvnosuchmatch -j DROP" }' | exec $REAL_IPTR "\$@"
EOF
  chmod 755 "$dir/iptables-restore"
}

# A firewall that meets an error must exit 2 and leave the rules it already has,
# never report success over chains it emptied or half-filled.
# expect_fail_closed LABEL SHIM COMMAND...: exit 2, guard state byte-identical.
expect_fail_closed() {
  local label=$1 dir="$SHIMS/$2" before after rc
  shift 2
  before=$(guard_state)
  PATH="$dir:$PATH" guard "$@" >/tmp/bvshim.out 2>&1; rc=$?
  after=$(guard_state)
  if [ "$rc" -eq 2 ]; then pass "$label: exit 2"; else fail "$label: exit $rc, expected 2: $(cat /tmp/bvshim.out)"; fi
  if [ "$before" = "$after" ]; then pass "$label: rules unchanged"; else fail "$label: rules changed: $(diff <(echo "$before") <(echo "$after") | head -20)"; fi
}

HOST_LISTENER_PID=""
UDP_HOST_PID=""
UDP_LAN_PID=""
TCP_LAN_PID=""
cleanup() {
  [ -n "$LOCK_HOLDER_PID" ] && kill "$LOCK_HOLDER_PID" >/dev/null 2>&1 || true
  guard remove >/dev/null 2>&1 || true
  docker rm -f bvt-int bvt-int-peer bvt-egress bvt-tunnel bvt-legacy bvt-v6 bvt-neighbor bvt-mount bvt-mount-file bvt-mount-vol bvt-lan bvt-lan-probe bvt-spoof >/dev/null 2>&1 || true
  [ -n "$UDP_HOST_PID" ] && kill "$UDP_HOST_PID" >/dev/null 2>&1 || true
  [ -n "$UDP_LAN_PID" ] && kill "$UDP_LAN_PID" >/dev/null 2>&1 || true
  [ -n "$TCP_LAN_PID" ] && kill "$TCP_LAN_PID" >/dev/null 2>&1 || true
  ip netns del bvt-lanns >/dev/null 2>&1 || true   # also removes the veth pair
  rm -f /tmp/bvudp-host.log /tmp/bvudp-lan.log
  docker volume rm bvt-vol-dir bvt-vol-parent >/dev/null 2>&1 || true
  for n in bvtest_internal bvtest_egress_out bvtest_tunnel_out bvtest_legacy bvtest_lan bvtest_v6 bvtest_neighbor; do
    docker network rm "$n" >/dev/null 2>&1 || true
  done
  [ -n "$HOST_LISTENER_PID" ] && kill "$HOST_LISTENER_PID" >/dev/null 2>&1 || true
  rm -rf /tmp/bvloc /tmp/bvlocp "$SHIMS"
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
# Sends raw packets with a forged source address (the default NET_RAW allows it).
docker run -d --name bvt-spoof  --network bvtest_egress_out "$PY_IMAGE" sleep 3600 >/dev/null

python3 -m http.server "$HOST_PORT" --bind :: >/dev/null 2>&1 &
HOST_LISTENER_PID=$!

# Spoofing fixture. A UDP listener on the host (INPUT path), and a stand-in
# "LAN host" in its own network namespace behind a veth pair, reached from the
# containers through FORWARD. Each listener logs one line per datagram.
SPOOF_SRC=10.99.99.99      # forged source: outside every guarded subnet
LANNS_HOST=10.254.0.1
LANNS_PEER=10.254.0.2
UDP_PORT=18095
udp_listener() { # ADDR LOGFILE; exec, so the background PID is python's and kill stops it
  exec python3 -u -c '
import socket, sys
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.bind((sys.argv[1], int(sys.argv[2])))
while True:
    d, a = s.recvfrom(200); print(a[0], d.decode(errors="replace"), flush=True)
' "$1" "$UDP_PORT" >"$2" 2>&1
}
udp_listener 0.0.0.0 /tmp/bvudp-host.log &
UDP_HOST_PID=$!
ip netns add bvt-lanns
ip link add bvt-lan0 type veth peer name bvt-lan1
ip link set bvt-lan1 netns bvt-lanns
ip addr add "$LANNS_HOST/24" dev bvt-lan0 && ip link set bvt-lan0 up
ip netns exec bvt-lanns ip addr add "$LANNS_PEER/24" dev bvt-lan1
ip netns exec bvt-lanns ip link set bvt-lan1 up
ip netns exec bvt-lanns ip link set lo up
ip netns exec bvt-lanns ip route add default via "$LANNS_HOST"
ip netns exec bvt-lanns bash -c "$(declare -f udp_listener); UDP_PORT=$UDP_PORT; udp_listener 0.0.0.0 /tmp/bvudp-lan.log" &
UDP_LAN_PID=$!

# The same stand-in LAN also carries prefixes in no special range, the way a
# LAN's global IPv6 prefix (or a LAN numbered from public IPv4 space) looks to
# the guard: only the host's own on-link routes say these are the LAN.
ONLINK_HOST=203.0.113.1    # TEST-NET-3
ONLINK_PEER=203.0.113.2
ONLINK6_HOST=2001:db8:b7:1::1
ONLINK6_PEER=2001:db8:b7:1::2
ip addr add "$ONLINK_HOST/24" dev bvt-lan0
ip netns exec bvt-lanns ip addr add "$ONLINK_PEER/24" dev bvt-lan1
ONLINK6=0
if [ "$V6" -eq 1 ] && ip -6 addr add "$ONLINK6_HOST/64" dev bvt-lan0 nodad 2>/dev/null \
  && ip netns exec bvt-lanns ip -6 addr add "$ONLINK6_PEER/64" dev bvt-lan1 nodad 2>/dev/null \
  && ip netns exec bvt-lanns ip -6 route add default via "$ONLINK6_HOST" 2>/dev/null; then
  ONLINK6=1
fi
if [ "$ONLINK6" -eq 1 ]; then bind=::; else bind=0.0.0.0; fi
ip netns exec bvt-lanns python3 -m http.server "$ONLINK_PORT" --bind "$bind" >/dev/null 2>&1 &
TCP_LAN_PID=$!
sleep 3

# send_udp CONTAINER SRC DST TAG: one UDP datagram to DST:UDP_PORT from SRC,
# built by hand on a raw socket so SRC can be any address.
send_udp() {
  docker exec "$1" python3 -c '
import socket, struct, sys
src, dst, port, tag = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4].encode()
udp = struct.pack("!HHHH", 40000, port, 8 + len(tag), 0) + tag
hdr = struct.pack("!BBHHHBBH4s4s", 0x45, 0, 20 + len(udp), 1, 0, 64, 17, 0,
                  socket.inet_aton(src), socket.inet_aton(dst))
s = socket.socket(socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_RAW)
s.sendto(hdr + udp, (dst, 0))
' "$2" "$3" "$UDP_PORT" "$4"
}
# expect_udp LOG TAG yes|no LABEL: whether a datagram tagged TAG arrived.
expect_udp() {
  sleep 1
  if grep -q -- "$2" "$1"; then got=yes; else got=no; fi
  if [ "$got" = "$3" ]; then pass "$4"; else fail "$4 (arrived: $got)"; fi
}
bridge_of() { # NETWORK: the Linux bridge Docker made for it
  local o
  o=$(docker network inspect -f '{{with index .Options "com.docker.network.bridge.name"}}{{.}}{{end}}' "$1")
  if [ -n "$o" ]; then echo "$o"; else echo "br-$(docker network inspect -f '{{.Id}}' "$1" | cut -c1-12)"; fi
}

HOST=$(host_ip)
NEIGHBOR_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' bvt-neighbor)
PEER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' bvt-int-peer)
GW_LEGACY=$(gateway_of bvtest_legacy)
GW_EGRESS=$(gateway_of bvtest_egress_out)
GW_TUNNEL=$(gateway_of bvtest_tunnel_out)
GW_INT=$(gateway_of bvtest_internal)
info "host=$HOST neighbor=$NEIGHBOR_IP peer=$PEER_IP v6=$V6 onlink6=$ONLINK6"

# The resolvers Docker's embedded DNS forwards to: Docker records them in each
# container's resolv.conf ("# ExtServers: [...]"). Fallback: the host's own
# resolv.conf, minus loopback stubs. On the NAS this is the LAN router: a
# private address, which the guard drops unless it is on the allowed-DNS list.
HOST_DNS=$(docker exec bvt-legacy grep '^# ExtServers:' /etc/resolv.conf 2>/dev/null \
  | grep -oE '([0-9]{1,3}\.){3}[0-9]{1,3}' | sort -u | tr '\n' ' ' | sed 's/ $//')
if [ -z "$HOST_DNS" ]; then
  HOST_DNS=$(awk '$1 == "nameserver" && $2 !~ /^127\./ && $2 !~ /:/ { print $2 }' /etc/resolv.conf | tr '\n' ' ' | sed 's/ $//')
fi
if [[ " $HOST_DNS " == *" $OTHER_DNS "* ]]; then OTHER_DNS=149.112.112.112; fi
info "resolvers Docker forwards to: ${HOST_DNS:-NONE}; non-listed resolver: $OTHER_DNS"
[ -n "$HOST_DNS" ] || fail "could not find the resolver Docker forwards to; the DNS checks cannot mean anything"
GUARD_DNS=$HOST_DNS

# --- baseline: prove the harness can see exposure --------------------------
expect_open bvt-legacy "$HOST" "$HOST_PORT"
expect_open bvt-legacy "$GW_LEGACY" "$HOST_PORT"
baseline bvt-legacy "$HOST" "$NEIGHBOR_PORT"
baseline bvt-legacy "$NEIGHBOR_IP" 8000
baseline bvt-int "$GW_INT" "$HOST_PORT"
[ "$V6" -eq 1 ] && baseline bvt-v6 "fd00:b7::1" "$HOST_PORT"
DOCKER_BEFORE=$(docker_chains_v4)

# DNS baseline: lookups work through Docker's resolver, and the resolver the
# guard will not allow answers too, so "blocked" after apply is the guard's doing.
expect_resolves bvt-egress cloudflare.com
expect_resolves bvt-egress example.net "$OTHER_DNS"

# On-link baseline: before the guard, a container reaches the stand-in LAN host
# on its public-range addresses.
ONLINK_BASE=no; tcp_from bvt-legacy "$ONLINK_PEER" "$ONLINK_PORT" && ONLINK_BASE=yes
ONLINK6_BASE=no
[ "$ONLINK6" -eq 1 ] && tcp_from bvt-v6 "$ONLINK6_PEER" "$ONLINK_PORT" && ONLINK6_BASE=yes
info "baseline: on-link public-range LAN reachable v4=$ONLINK_BASE v6=$ONLINK6_BASE"

# Spoofing baseline: the stand-in LAN host is reachable at all, and whether a
# forged source gets through before the guard (reverse-path filtering may stop
# it on some hosts; then the post-apply spoofing checks prove nothing and skip).
SPOOF_REAL_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' bvt-spoof)
info "rp_filter: all=$(cat /proc/sys/net/ipv4/conf/all/rp_filter) default=$(cat /proc/sys/net/ipv4/conf/default/rp_filter)"
send_udp bvt-spoof "$SPOOF_REAL_IP" "$LANNS_PEER" real-to-lan-before
expect_udp /tmp/bvudp-lan.log real-to-lan-before yes "baseline: a container reaches the stand-in LAN host"
send_udp bvt-spoof "$SPOOF_SRC" "$LANNS_PEER" spoof-to-lan-before
send_udp bvt-spoof "$SPOOF_SRC" "$HOST" spoof-to-host-before
sleep 1
SPOOF_LAN_BASE=no; grep -q spoof-to-lan-before /tmp/bvudp-lan.log && SPOOF_LAN_BASE=yes
SPOOF_HOST_BASE=no; grep -q spoof-to-host-before /tmp/bvudp-host.log && SPOOF_HOST_BASE=yes
info "baseline: forged source reaches LAN=$SPOOF_LAN_BASE host=$SPOOF_HOST_BASE"

# --- probe-isolation.sh helpers ---------------------------------------------------------
PROBE_OUT=/tmp/bvprobe.out
# run_probe ARGS...: run probe-isolation.sh on the test project, output to
# $PROBE_OUT (shown in this log), status returned. The time limit only stops a
# broken probe from hanging the harness; a full healthy run takes a few minutes.
run_probe() {
  timeout 600 bash "$PROBE" --project "$PROJECT" --image "$PROBE_IMAGE" "$@" >"$PROBE_OUT" 2>&1
  local rc=$?
  sed 's/^/      | /' "$PROBE_OUT"
  return "$rc"
}
# probe_row NETWORK ROLE TARGET HOST CONTAINER RESULT: the table holds exactly
# this row, formatted the way probe-isolation.sh formats it.
probe_row() {
  grep -qxF -- "$(printf '%-28s %-13s %-30s %-8s %-9s %s' "$@")" "$PROBE_OUT"
}
expect_probe_row() {
  if probe_row "$@"; then pass "probe row: $*"; else fail "probe row missing: $*"; fi
}
expect_probe_rc() { # WANT GOT LABEL
  if [ "$2" -eq "$1" ]; then pass "probe $3: exit $1"; else fail "probe $3: exit $2, expected $1"; fi
}

# --- probe-isolation.sh sees exposure ------------------------------------------------
# Before apply the host listener is reachable from the project's networks (the
# baseline above proved it), so a probe that reports everything as blocked, or
# counts a failure to probe as "blocked", is caught here.
v6_target=()
[ "$V6" -eq 1 ] && v6_target=(--target "[fd00:b7::1]:$HOST_PORT")
run_probe --target "$HOST:$HOST_PORT" "${v6_target[@]}" --public "$PUBLIC_IP:443"; rc=$?
expect_probe_rc 1 "$rc" "before apply"
expect_probe_row bvtest_legacy transitional "$HOST:$HOST_PORT" open OPEN FAIL
# A bracketed IPv6 target is really tried: the v6 gateway is open before apply.
[ "$V6" -eq 1 ] && expect_probe_row bvtest_v6 transitional "[fd00:b7::1]:$HOST_PORT" open OPEN FAIL

# --- roles -------------------------------------------------------------------
roles=$(guard roles)
for want in "bvtest_internal internal" "bvtest_egress_out egress" "bvtest_tunnel_out tunnel" "bvtest_legacy transitional" "bvtest_lan lan"; do
  if grep -qx "$want" <<<"$roles"; then pass "role: $want"; else fail "role missing: $want (got: $roles)"; fi
done

# --- apply -------------------------------------------------------------------
if guard apply; then pass "apply exit 0"; else fail "apply failed"; fi
guard status | sed 's/^/      | /'   # the rules under test, for the log

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

# Captured, not piped into grep -q: under pipefail, grep -q exiting at the first
# match can SIGPIPE iptables and turn a match into a failure.
fwd_rules=$(iptables -w -S BV-GUARD)
if grep -q -- '--dports 443,7844' <<<"$fwd_rules"; then pass "tunnel allows tcp 443,7844"; else fail "tunnel tcp rule missing"; fi
if grep -q -- '-p udp -m udp --dport 7844' <<<"$fwd_rules"; then pass "tunnel allows udp 7844"; else fail "tunnel udp rule missing"; fi
for p in 0.0.0.0/8 192.0.0.0/24 198.18.0.0/15; do
  # Exact fields; iptables prints -d before -i.
  if awk -v p="$p" 'NF == 8 && $2 == "BV-GUARD" && $3 == "-d" && $4 == p && $5 == "-i" && $8 == "BV-LOG-DROP" { f = 1 }
      END { exit !f }' <<<"$fwd_rules"; then pass "special range $p dropped"; else fail "special range $p not dropped"; fi
done

# DNS: the allowed resolver answers for the roles that need the internet, and
# nothing else on port 53 is reachable from them.
expect_resolves   bvt-egress one.one.one.one
expect_resolves   bvt-tunnel dns.google
expect_resolves   bvt-legacy example.com
expect_unresolved bvt-lan-probe example.org        # lan: nothing goes out, DNS included
expect_unresolved bvt-int example.org              # internal: likewise
expect_unresolved bvt-egress example.net "$OTHER_DNS"
expect_unresolved bvt-tunnel example.net "$OTHER_DNS"
first_dns=${HOST_DNS%% *}
if tcp_from bvt-egress "$first_dns" 53; then
  pass "open    bvt-egress -> $first_dns:53 over TCP (DNS falls back to TCP)"
else
  # Not every resolver accepts TCP; only a resolver that does proves the rule.
  if timeout 3 bash -c "</dev/tcp/$first_dns/53" 2>/dev/null; then
    fail "expected OPEN bvt-egress -> $first_dns:53 over TCP (the host reaches it)"
  else
    info "SKIPPED DNS-over-TCP check: $first_dns does not answer TCP from this host either"
  fi
fi

# A LAN known only from the host's on-link routes is the LAN all the same.
if [ "$ONLINK_BASE" = yes ]; then
  expect_blocked bvt-legacy "$ONLINK_PEER" "$ONLINK_PORT"
else
  skip "on-link public-range LAN check (IPv4): the stand-in was unreachable even before apply"
fi
if [ "$ONLINK6_BASE" = yes ]; then
  expect_blocked bvt-v6 "$ONLINK6_PEER" "$ONLINK_PORT"
else
  skip "on-link public-range LAN check (IPv6): the stand-in was unreachable even before apply"
fi

# Forged sources: every rule keys on the network's bridge, so a packet from a
# guarded network is judged by where it came from, not by what it claims.
if [ "$SPOOF_LAN_BASE" = yes ]; then
  send_udp bvt-spoof "$SPOOF_SRC" "$LANNS_PEER" spoof-to-lan-after
  expect_udp /tmp/bvudp-lan.log spoof-to-lan-after no "forged source cannot reach the LAN"
else
  skip "forged-source LAN check: forged packets did not arrive even before apply"
fi
if [ "$SPOOF_HOST_BASE" = yes ]; then
  send_udp bvt-spoof "$SPOOF_SRC" "$HOST" spoof-to-host-after
  expect_udp /tmp/bvudp-host.log spoof-to-host-after no "forged source cannot reach the host"
else
  skip "forged-source host check: forged packets did not arrive even before apply"
fi
send_udp bvt-spoof "$SPOOF_REAL_IP" "$LANNS_PEER" real-to-lan-after
expect_udp /tmp/bvudp-lan.log real-to-lan-after no "real source cannot reach the LAN either"

if [ "$V6" -eq 1 ]; then
  V6_BR=$(bridge_of bvtest_v6)
  in6_rules=$(ip6tables -w -S BV-GUARD-IN)
  if grep -q -- "-i $V6_BR " <<<"$in6_rules"; then pass "IPv6 bridge $V6_BR guarded"; else fail "IPv6 bridge $V6_BR not in ip6tables BV-GUARD-IN"; fi
  expect_blocked bvt-v6 "fd00:b7::1" "$HOST_PORT"
  # Neighbour discovery must survive the guard: with both neighbour caches
  # emptied, reaching the container needs a fresh NS/NA exchange.
  V6_ADDR=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.GlobalIPv6Address}}{{end}}' bvt-v6)
  ip -6 neigh flush dev "$V6_BR" >/dev/null 2>&1
  docker exec bvt-v6 ip -6 neigh flush dev eth0 >/dev/null 2>&1
  if ping -6 -c1 -W3 "$V6_ADDR" >/dev/null 2>&1; then
    pass "IPv6 neighbour discovery works: host reaches $V6_ADDR after a cache flush"
  else
    fail "host cannot reach $V6_ADDR after a neighbour-cache flush (NDP dropped)"
  fi
  # The bridge's link-local address is the host too.
  V6_LL=$(ip -6 addr show dev "$V6_BR" scope link | awk '/inet6/ { sub(/\/.*/, "", $2); print $2; exit }')
  if [ -n "$V6_LL" ]; then
    expect_blocked bvt-v6 "$V6_LL%eth0" "$HOST_PORT"
  else
    fail "bridge $V6_BR has no link-local address to test"
  fi
else
  skip "IPv6 checks: this Docker host cannot create IPv6 networks"
fi

# --- probe-isolation.sh agrees -----------------------------------------------
# A gateway on the host listener's port gives gateway rows that are real PASSes
# (the host answers there, the probe must not); a port nothing listens on must
# read N/A and must not fail the run.
CLOSED_PORT=18096
run_probe --target "$HOST:$HOST_PORT" --target "$HOST:$NEIGHBOR_PORT" --target "$GW_LEGACY:$HOST_PORT" \
  --target "$HOST:$CLOSED_PORT" "${v6_target[@]}" --public "$PUBLIC_IP:443"; rc=$?
expect_probe_rc 0 "$rc" "after apply"
expect_probe_row bvtest_legacy transitional "$GW_LEGACY:$HOST_PORT" open blocked PASS
expect_probe_row bvtest_internal internal "$HOST:$HOST_PORT" open blocked PASS
expect_probe_row bvtest_egress_out egress "$PUBLIC_IP:443 (public)" open open PASS
expect_probe_row bvtest_lan lan "$PUBLIC_IP:443 (public)" open blocked PASS
expect_probe_row bvtest_legacy transitional "$HOST:$CLOSED_PORT" closed blocked "N/A (host cannot reach)"
if grep -Eq '^N/A rows: [1-9]' "$PROBE_OUT"; then pass "probe summary counts N/A rows"; else fail "probe summary has no N/A count"; fi
if [ "$V6" -eq 1 ]; then
  # The host's own connection to fd00:b7::1 runs over lo, which the guard's
  # INPUT chain (keyed on the container bridges) leaves alone, so the host
  # reaches its gateway address and the container does not: a real PASS.
  expect_probe_row bvtest_v6 transitional "[fd00:b7::1]:$HOST_PORT" open blocked PASS
  # Before Docker 28 no gateway is recorded for an IPv6 subnet and the probe
  # derives it ("(derived)"); from 28 on Docker records it. Either way, tried.
  if grep -Eq '^bvtest_v6 +[a-z]+ +\[fd00:b7::1\]:22( \(derived\))? ' "$PROBE_OUT"; then
    pass "probe tries the IPv6 subnet's gateway"
  else
    fail "probe skipped the IPv6 subnet's gateway"
  fi
fi

# A probe that cannot run the test must say so (exit 2), never report "blocked".
run_probe --image busybox:latest; rc=$?
expect_probe_rc 2 "$rc" "with an image that has no bash"
# docker exec failing on every connection attempt: rows are ERROR, never PASS.
shim probexec docker "$REAL_DOCKER" '[ "$1" = exec ] && [[ "$*" == *dev/tcp* ]]'
PATH="$SHIMS/probexec:$PATH" run_probe; rc=$?
expect_probe_rc 2 "$rc" "when every docker exec fails"
if grep -q ' ERROR$' "$PROBE_OUT" && ! grep -q ' PASS$' "$PROBE_OUT"; then
  pass "failed probes read ERROR, not PASS"
else
  fail "failed probes did not all read ERROR"
fi
# A public target the host itself cannot reach (TEST-NET-1, never routed)
# proves nothing about public isolation: no public row may claim PASS.
run_probe --public 192.0.2.1:443; rc=$?
expect_probe_rc 2 "$rc" "with an unreachable --public"
if grep -q '(public).* PASS$' "$PROBE_OUT"; then
  fail "a public row claims PASS although the host cannot reach --public"
else
  pass "no public row claims PASS when the host cannot reach --public"
fi
# Malformed targets are usage errors, found before anything is probed.
for bad in "--target $HOST" "--target $HOST:0" "--target $HOST:65536" "--target fd00:b7::1:80" \
  "--target" "--public $PUBLIC_IP" "--public example.invalid:443"; do
  # shellcheck disable=SC2086 # word splitting builds the argument list
  timeout 60 bash "$PROBE" --project "$PROJECT" $bad >/dev/null 2>&1; rc=$?
  expect_probe_rc 2 "$rc" "with $bad"
done
rm -f "$PROBE_OUT"

# --- idempotency ---------------------------------------------------------------
before=$(chains_v4)
out=$(guard apply)
after=$(chains_v4)
if [ "$before" = "$after" ]; then pass "second apply leaves rules identical"; else fail "second apply changed the rules"; fi
if grep -q 'nothing to change' <<<"$out"; then pass "second apply reports nothing to change"; else fail "second apply output: $out"; fi
# Both streams: cron mails stderr as well as stdout.
out=$(guard apply --quiet 2>&1)
if [ -z "$out" ]; then pass "--quiet prints nothing when nothing changed"; else fail "--quiet printed on a clean run: $out"; fi

# --- drift detection and repair ----------------------------------------------------
check_is() { guard check >/dev/null 2>&1; local rc=$?; if [ "$rc" -eq "$1" ]; then pass "check exit $1 ($2)"; else fail "check exit $rc, expected $1 ($2)"; fi; }
check_is 0 "clean"
iptables -w -D BV-GUARD 3
check_is 1 "rule deleted"
out=$(guard apply --quiet)
if grep -q '^egress-guard: repaired' <<<"$out"; then pass "apply --quiet reports the repair"; else fail "repair not reported: $out"; fi
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
iptables -w -A DOCKER-USER -j BV-GUARD
check_is 1 "jump duplicated"
guard apply --quiet >/dev/null
check_is 0 "duplicate jump removed"
# A jump to the guard from the parent it no longer uses (IPv6: FORWARD, from a
# run before Docker managed ip6tables) is the guard's own leftover: removed.
for fam in 4 6; do
  [ "$fam" = 6 ] && [ "$V6" -eq 0 ] && continue
  if [ "$fam" = 4 ]; then t=iptables; else t=ip6tables; fi
  $t -w -A FORWARD -j BV-GUARD
  check_is 1 "IPv$fam stray FORWARD -> BV-GUARD jump"
  guard apply --quiet >/dev/null
  fwd=$($t -w -S FORWARD)
  if grep -q -- '-j BV-GUARD$' <<<"$fwd"; then fail "IPv$fam stray FORWARD jump left in place"; else pass "IPv$fam stray FORWARD jump removed"; fi
  check_is 0 "IPv$fam after removing the stray jump"
done

# --- the allowed-DNS list ---------------------------------------------------------
# Changing the list is drift until apply.
GUARD_DNS="$HOST_DNS $OTHER_DNS"
check_is 1 "allowed-DNS list changed"
GUARD_DNS=$HOST_DNS
check_is 0 "allowed-DNS list back as applied"
# An empty list fails closed (no DNS for anyone) and says so on every run,
# --quiet or not: cron mails it.
GUARD_DNS=""
out=$(guard apply --quiet 2>&1); rc=$?
if [ "$rc" -eq 0 ] && grep -q 'BV_ALLOWED_DNS is empty' <<<"$out"; then
  pass "empty allowed-DNS list: apply warns, even with --quiet"
else
  fail "empty allowed-DNS list: rc=$rc output: $out"
fi
expect_unresolved bvt-egress www.example.com
expect_unresolved bvt-legacy www.example.org
out=$(guard check --quiet 2>&1); rc=$?
if [ "$rc" -eq 0 ] && grep -q 'BV_ALLOWED_DNS is empty' <<<"$out"; then pass "empty allowed-DNS list: check warns too"; else fail "empty allowed-DNS list, check: rc=$rc output: $out"; fi
GUARD_DNS=$HOST_DNS
guard apply --quiet >/dev/null
expect_resolves bvt-egress www.example.com
# Only single resolver addresses a container can reach through its bridge: no
# prefixes (a /8 would open DNS to the whole LAN), no ports, no loopback,
# link-local, multicast or unspecified addresses, none of the host's own.
for bad in 10.0.0.0/8 1.1.1.1:53 300.1.1.1 not-an-ip 127.0.0.53 0.0.0.0 169.254.1.1 224.0.0.251 \
  255.255.255.255 :: ::1 fe80::1 ff02::1 2001:db8::/64 "$HOST"; do
  GUARD_DNS="$HOST_DNS $bad" expect_fail_closed "apply, allowed DNS includes '$bad'" none apply
done
GUARD_DNS=$HOST_DNS

# A project with no networks guards nothing; say so on every run, --quiet or not.
out=$(BV_PROJECTS="$PROJECT bvnosuchproject" BV_ALLOWED_DNS=$GUARD_DNS BV_GUARD_SKIP_LOCATION_CHECK=1 bash "$GUARD" check --quiet 2>&1); rc=$?
if [ "$rc" -eq 0 ] && grep -q 'bvnosuchproject has no networks' <<<"$out"; then
  pass "a project with no networks is reported, even with --quiet"
else
  fail "project with no networks: rc=$rc output: $out"
fi

# --- fail closed on tool errors -------------------------------------------------------
shim netls docker "$REAL_DOCKER" '[ "$1" = network ] && [ "$2" = ls ]'
shim netinspect docker "$REAL_DOCKER" '[ "$1" = network ] && [ "$2" = inspect ]'
expect_fail_closed "apply, docker network ls fails" netls apply
expect_fail_closed "check, docker network ls fails" netls check
expect_fail_closed "apply, docker network inspect fails" netinspect apply

# Rules in DOCKER-USER see no traffic unless FORWARD jumps there. That is
# Docker's rule, not the guard's to repair: refuse, loudly, every run.
iptables -w -D FORWARD -j DOCKER-USER
expect_fail_closed "apply, FORWARD does not jump to DOCKER-USER" none apply
expect_fail_closed "check, FORWARD does not jump to DOCKER-USER" none check
iptables -w -I FORWARD 1 -j DOCKER-USER
check_is 0 "FORWARD -> DOCKER-USER restored"

# Repair run whose fill of BV-GUARD fails part-way: the drifted chain stays as it was.
iptables -w -D BV-GUARD 3
fill_fault fill BV-GUARD
expect_fail_closed "repair, BV-GUARD fill fails part-way" fill apply
if grep -q 'repaired IPv4 chain BV-GUARD$' /tmp/bvshim.out; then fail "failed repair still printed 'repaired'"; else pass "failed repair does not claim a repair"; fi
guard apply --quiet >/dev/null
check_is 0 "repaired after the fault is gone"

# The desired policy is computed in a scratch chain; if that fill fails, a clean
# system must not be reported as drift (1) against a truncated policy.
fill_fault scratch BV-GUARD-NEW
expect_fail_closed "check, scratch fill fails" scratch check
expect_fail_closed "apply, scratch fill fails" scratch apply

# Jump repair whose insert fails must not leave the jump deleted. Either way the
# guard repairs jumps (delete then insert with iptables, or one iptables-restore
# transaction), the shim makes the insert fail after the delete was attempted.
iptables -w -I DOCKER-USER 1 -j RETURN
mkdir -p "$SHIMS/jump"
cat >"$SHIMS/jump/iptables" <<EOF
#!/usr/bin/env bash
if [ "\$2" = -I ] && [ "\$3" = DOCKER-USER ]; then echo "shim: injected iptables failure" >&2; exit 1; fi
exec $REAL_IPT "\$@"
EOF
cat >"$SHIMS/jump/iptables-restore" <<EOF
#!/usr/bin/env bash
awk '{ print } /^-I DOCKER-USER / { print "-A DOCKER-USER -m bvnosuchmatch -j DROP" }' | exec $REAL_IPTR "\$@"
EOF
chmod 755 "$SHIMS/jump/iptables" "$SHIMS/jump/iptables-restore"
expect_fail_closed "jump repair, insert fails" jump apply
iptables -w -D DOCKER-USER -j RETURN 2>/dev/null || true
check_is 0 "jump intact after the failed repair"
guard apply --quiet >/dev/null   # keep later blocks independent of this one's outcome

# Concurrent runs share the scratch chains, so a second run waits for the lock
# and then gives up with exit 2 rather than racing the first.
# exec: the holder's PID is the process that holds the lock, so kill releases it.
( exec 9>>"$LOCK" && flock 9 && exec sleep 90 ) &
LOCK_HOLDER_PID=$!
sleep 1
start=$SECONDS
guard check >/tmp/bvshim.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'still holds the lock' /tmp/bvshim.out; then
  pass "a second run gives up on the held lock with exit 2 (after $((SECONDS - start))s)"
else
  fail "lock: rc=$rc $(cat /tmp/bvshim.out)"
fi
kill "$LOCK_HOLDER_PID" >/dev/null 2>&1; wait "$LOCK_HOLDER_PID" 2>/dev/null; LOCK_HOLDER_PID=""
check_is 0 "lock released"

# Only root may hold the lock: a lock file anyone can open lets any local user
# stall every run.
if [ "$(stat -c '%a %U' "$LOCK")" = "600 root" ]; then pass "lock file is 600 root"; else fail "lock file is $(stat -c '%a %U' "$LOCK")"; fi
chown 65534 "$LOCK"
guard check >/tmp/bvshim.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'lock file' /tmp/bvshim.out; then pass "refuses a lock file not owned by root"; else fail "foreign lock owner: rc=$rc $(cat /tmp/bvshim.out)"; fi
chown 0 "$LOCK"

# The lock is taken after the Docker wait: a boot-time apply that is still
# waiting for Docker must not block the per-minute runs.
# Its failure path also probes for Docker rules in the legacy backend; that
# probe must not create a legacy table (after which every iptables call warns
# on stderr, and a cron apply --quiet mails that warning every minute).
legacy_filter() { grep -qx filter /proc/net/ip_tables_names 2>/dev/null; }
legacy_before=absent; legacy_filter && legacy_before=present
shim noinfo docker "$REAL_DOCKER" '[ "$1" = info ]'
PATH="$SHIMS/noinfo:$PATH" guard apply --wait-for-docker 20 >/dev/null 2>&1 &
waiter=$!
sleep 2
start=$SECONDS
guard check >/tmp/bvshim.out 2>&1; rc=$?
took=$((SECONDS - start))
if [ "$rc" -eq 0 ] && [ "$took" -lt 10 ]; then pass "a run waiting for Docker does not hold the lock (check took ${took}s)"; else fail "check during a Docker wait: rc=$rc after ${took}s $(cat /tmp/bvshim.out)"; fi
wait "$waiter"; rc=$?
if [ "$rc" -eq 2 ]; then pass "apply gives up with exit 2 when Docker never comes up"; else fail "apply with Docker down: rc=$rc"; fi
if [ "$legacy_before" = present ]; then
  skip "legacy-table check: a legacy filter table already existed before this run"
elif legacy_filter; then
  fail "the Docker-down path created a legacy iptables filter table"
else
  pass "the Docker-down path created no legacy iptables table"
fi
rm -f /tmp/bvshim.out

# --- location safety ------------------------------------------------------------------
mkdir -p /tmp/bvloc && cp "$GUARD" /tmp/bvloc/egress-guard.sh
chown -R root:root /tmp/bvloc && chmod 700 /tmp/bvloc && chmod 700 /tmp/bvloc/egress-guard.sh
docker run -d --name bvt-mount -v /tmp/bvloc:/x:ro "$PROBE_IMAGE" sleep 3600 >/dev/null
guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'mounted into a container' /tmp/bvloc.out; then pass "refuses to run from a container-mounted directory"; else fail "location check: rc=$rc $(cat /tmp/bvloc.out)"; fi
# If Docker cannot say what is mounted, the check must fail, not pass with nothing checked.
shim ps docker "$REAL_DOCKER" '[ "$1" = ps ]'
shim inspect docker "$REAL_DOCKER" '[ "$1" = inspect ]'
for s in "ps:could not list containers" "inspect:could not inspect containers"; do
  PATH="$SHIMS/${s%%:*}:$PATH" guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
  if [ "$rc" -eq 2 ] && grep -q "${s#*:}" /tmp/bvloc.out; then
    pass "mount check fails closed when docker ${s%%:*} fails"
  else
    fail "mount check with failing ${s%%:*}: rc=$rc $(cat /tmp/bvloc.out)"
  fi
done
docker rm -f bvt-mount >/dev/null
# A bind mount of the script file itself is as dangerous as one of its directory.
docker run -d --name bvt-mount-file -v /tmp/bvloc/egress-guard.sh:/x.sh:ro "$PROBE_IMAGE" sleep 3600 >/dev/null
guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'mounted into a container' /tmp/bvloc.out; then pass "refuses to run when the script file itself is mounted"; else fail "file-mount check: rc=$rc $(cat /tmp/bvloc.out)"; fi
docker rm -f bvt-mount-file >/dev/null
# A named volume bound to a host path shows up in .Mounts as the volume's own
# _data directory, not the host path, so the guard must resolve the volume.
# expect_volume_refused VOLUME DEVICE LABEL
expect_volume_refused() {
  docker volume create --opt type=none --opt o=bind --opt device="$2" "$1" >/dev/null
  docker run -d --name bvt-mount-vol -v "$1:/x:ro" "$PROBE_IMAGE" sleep 3600 >/dev/null
  guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
  if [ "$rc" -eq 2 ] && grep -q 'mounted into a container' /tmp/bvloc.out; then pass "refuses $3"; else fail "$3: rc=$rc $(cat /tmp/bvloc.out)"; fi
}
expect_volume_refused bvt-vol-dir /tmp/bvloc "a named volume bound to the script's directory"
shim volinspect docker "$REAL_DOCKER" '[ "$1" = volume ] && [ "$2" = inspect ]'
PATH="$SHIMS/volinspect:$PATH" guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'could not inspect volume' /tmp/bvloc.out; then
  pass "mount check fails closed when docker volume inspect fails"
else
  fail "mount check with failing volume inspect: rc=$rc $(cat /tmp/bvloc.out)"
fi
docker rm -f bvt-mount-vol >/dev/null
expect_volume_refused bvt-vol-parent /tmp "a named volume bound to a parent of the script's directory"
docker rm -f bvt-mount-vol >/dev/null
docker volume rm bvt-vol-dir bvt-vol-parent >/dev/null
chmod 775 /tmp/bvloc
guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q 'writable' /tmp/bvloc.out; then pass "refuses a group-writable directory"; else fail "permission check: rc=$rc $(cat /tmp/bvloc.out)"; fi
chmod 700 /tmp/bvloc
# /tmp is world-writable but sticky and root's: no one else can rename or
# replace the root-owned directory inside it, so that parent is accepted.
guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 0 ]; then pass "safe location accepted"; else fail "safe location rejected: rc=$rc $(cat /tmp/bvloc.out)"; fi
# A container that disappears between listing and inspecting is not an error:
# the check lists again, once.
mkdir -p "$SHIMS/vanish"
cat >"$SHIMS/vanish/docker" <<EOF
#!/usr/bin/env bash
if [ "\$1" = inspect ] && [ ! -e $SHIMS/vanish/once ]; then
  touch $SHIMS/vanish/once; echo "Error: No such object: bvt-gone" >&2; exit 1
fi
exec $REAL_DOCKER "\$@"
EOF
chmod 755 "$SHIMS/vanish/docker"
PATH="$SHIMS/vanish:$PATH" guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 0 ]; then pass "mount check lists again when a container vanishes mid-check"; else fail "vanished container: rc=$rc $(cat /tmp/bvloc.out)"; fi

# Every directory above the script counts: whoever can rename a parent can
# swap in a script of their own.
mkdir -p /tmp/bvlocp/inner && cp "$GUARD" /tmp/bvlocp/inner/egress-guard.sh
chown -R root:root /tmp/bvlocp && chmod 700 /tmp/bvlocp/inner /tmp/bvlocp/inner/egress-guard.sh
LOC_GUARD=/tmp/bvlocp/inner/egress-guard.sh
chmod 775 /tmp/bvlocp
guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q '/tmp/bvlocp .*writable' /tmp/bvloc.out; then pass "refuses a group-writable parent directory"; else fail "parent permission check: rc=$rc $(cat /tmp/bvloc.out)"; fi
chmod 755 /tmp/bvlocp && chown 65534 /tmp/bvlocp
guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 2 ] && grep -q '/tmp/bvlocp must be owned by root' /tmp/bvloc.out; then pass "refuses a parent directory not owned by root"; else fail "parent owner check: rc=$rc $(cat /tmp/bvloc.out)"; fi
chown 0 /tmp/bvlocp && chmod 1777 /tmp/bvlocp
guard_loc check >/tmp/bvloc.out 2>&1; rc=$?
if [ "$rc" -eq 0 ]; then pass "accepts a sticky world-writable parent owned by root"; else fail "sticky parent rejected: rc=$rc $(cat /tmp/bvloc.out)"; fi
LOC_GUARD=/tmp/bvloc/egress-guard.sh
rm -rf /tmp/bvlocp /tmp/bvloc.out

# --- remove ---------------------------------------------------------------------------
guard remove >/dev/null
# Captured first: a SIGPIPE from "iptables -S | grep -q" would read as "no match"
# here, which is a PASS, so leftover rules could slip through.
all4=$(iptables -w -S)
if ! grep -q 'BV-' <<<"$all4"; then pass "remove: no BV- rules left (IPv4)"; else fail "remove left IPv4 rules"; fi
all6=""
command -v ip6tables >/dev/null && all6=$(ip6tables -w -S 2>/dev/null)
if grep -q 'BV-' <<<"$all6"; then fail "remove left IPv6 rules"; else pass "remove: no BV- rules left (IPv6)"; fi
if [ "$(docker_chains_v4)" = "$DOCKER_BEFORE" ]; then pass "Docker's own chains untouched"; else fail "Docker's chains changed"; fi

echo
echo "passes=$PASSES fails=$FAILS"
[ "$FAILS" -eq 0 ]
