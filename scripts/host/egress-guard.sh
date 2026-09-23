#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# egress-guard.sh — keep a Docker Compose project's containers away from the
# LAN and from the host they run on, using iptables rules that live in the host
# kernel, where no container can see or change them.
#
# Usage:
#   egress-guard.sh apply [--wait-for-docker SECONDS] [--quiet]
#   egress-guard.sh check     exit 0 = rules current, 1 = drift, 2 = error
#   egress-guard.sh status    print the guard's rules
#   egress-guard.sh roles     print "<network> <role>" for each guarded network
#   egress-guard.sh remove    delete everything this script created
#
# Environment:
#   BV_PROJECTS        compose project names to guard (default: bottlevault)
#   BV_ROLE_OVERRIDES  space-separated network=role pairs
#   BV_GUARD_SKIP_LOCATION_CHECK=1   test harness only; never on a real host
#
# Policy for traffic that ORIGINATES in a guarded network's subnet:
#   replies to connections opened from outside ........ allowed
#   traffic to the same subnet ......................... allowed
#   private and special ranges (LAN, other Docker
#     networks, CGNAT/VPN, link-local, loopback,
#     multicast) ....................................... logged, dropped
#   anything addressed to the host itself ............. logged, dropped
#   everything else, by role:
#     internal      Docker internal network ........... dropped
#     lan           name ends in _lan (exists only to
#                   publish a LAN port) ............... dropped
#     tunnel        name ends in _tunnel_out .......... tcp 443/7844, udp 7844
#     egress        name ends in _egress_out .......... tcp 443
#     transitional  any other non-internal network .... allowed
#
# Run as root, from a root-owned directory that is not mounted into any
# container (the script refuses otherwise). Install: DEPLOY.md §9.
set -euo pipefail

readonly FWD=BV-GUARD
readonly IN=BV-GUARD-IN
readonly DROP=BV-LOG-DROP
readonly SPECIAL_V4="10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 127.0.0.0/8 224.0.0.0/4 240.0.0.0/4"
readonly SPECIAL_V6="fc00::/7 fe80::/10 ff00::/8 ::1/128"

PROJECTS="${BV_PROJECTS:-bottlevault}"
QUIET=0
WAIT=0
NETS_FILE=""
FAMILIES="4"
CHANGED=0

die()  { echo "egress-guard: ERROR: $*" >&2; exit 2; }
say()  { [ "$QUIET" -eq 1 ] || echo "egress-guard: $*"; }
warn() { [ "$QUIET" -eq 1 ] || echo "egress-guard: WARN: $*" >&2; }
note() { echo "egress-guard: $*"; }   # always printed: repairs and drift

cleanup_tmp() { if [ -n "$NETS_FILE" ]; then rm -f "$NETS_FILE"; fi; }
trap cleanup_tmp EXIT

ipt() {
  local fam=$1
  shift
  if [ "$fam" = 4 ]; then iptables -w "$@"; else ip6tables -w "$@"; fi
}

usage() {
  echo "usage: egress-guard.sh apply [--wait-for-docker SECONDS] [--quiet] | check | status | roles | remove" >&2
  exit 2
}

require_root() { [ "$(id -u)" -eq 0 ] || die "must run as root"; }

require_tools() {
  command -v docker >/dev/null || die "docker CLI not found"
  command -v iptables >/dev/null || die "iptables not found"
}

wait_for_docker() {
  local deadline=$((SECONDS + WAIT))
  while :; do
    if timeout 10 docker info >/dev/null 2>&1 && iptables -w -S DOCKER-USER >/dev/null 2>&1; then
      return 0
    fi
    [ "$SECONDS" -lt "$deadline" ] || break
    sleep 5
  done
  if command -v iptables-legacy >/dev/null && iptables-legacy -S DOCKER-USER >/dev/null 2>&1; then
    die "Docker's rules are in the legacy iptables backend but 'iptables' is $(iptables -V); refusing to guard the wrong table"
  fi
  die "Docker is not running or its DOCKER-USER chain is missing (waited ${WAIT}s)"
}

# The script runs as root, from cron. If a container could write to it, that
# container could get root on the host at the next run.
check_location() {
  if [ "${BV_GUARD_SKIP_LOCATION_CHECK:-0}" = 1 ]; then return 0; fi
  local self dir p owner mode src
  self=$(readlink -f "$0")
  dir=$(dirname "$self")
  for p in "$self" "$dir"; do
    owner=$(stat -c '%U' "$p")
    mode=$(stat -c '%a' "$p")
    [ "$owner" = root ] || die "$p must be owned by root (owner: $owner)"
    if (( (8#$mode & 8#022) != 0 )); then
      die "$p must not be group- or world-writable (mode $mode)"
    fi
  done
  while IFS= read -r src; do
    [ -n "$src" ] || continue
    src=$(readlink -f "$src" 2>/dev/null || printf '%s' "$src")
    src=${src%/}
    case "$dir/" in
      "$src"/*) die "$dir is inside $src, which is mounted into a container; a container could rewrite this root-run script" ;;
    esac
  done < <(docker ps -aq | xargs -r docker inspect --format '{{range .Mounts}}{{.Source}}{{"\n"}}{{end}}')
}

role_of() {
  local name=$1 internal=$2 pair
  for pair in ${BV_ROLE_OVERRIDES:-}; do
    if [ "${pair%%=*}" = "$name" ]; then
      printf '%s\n' "${pair#*=}"
      return
    fi
  done
  if [ "$internal" = true ]; then echo internal
  elif [[ "$name" == *_lan ]]; then echo lan
  elif [[ "$name" == *_tunnel_out ]]; then echo tunnel
  elif [[ "$name" == *_egress_out ]]; then echo egress
  else echo transitional
  fi
}

# Writes "name|role|subnet subnet ..." lines, sorted by name so rule order is stable.
load_networks() {
  NETS_FILE=$(mktemp)
  local project net info internal subnets role
  for project in $PROJECTS; do
    while IFS= read -r net; do
      [ -n "$net" ] || continue
      info=$(docker network inspect "$net" --format '{{.Internal}}|{{range .IPAM.Config}}{{.Subnet}} {{end}}')
      internal=${info%%|*}
      subnets=${info#*|}
      role=$(role_of "$net" "$internal")
      case "$role" in
        internal|lan|tunnel|egress|transitional) ;;
        *) die "unknown role '$role' for network $net (check BV_ROLE_OVERRIDES)" ;;
      esac
      if [ "$role" = transitional ]; then
        warn "network $net has no explicit policy; allowing public destinations on any port (transitional)"
      fi
      printf '%s|%s|%s\n' "$net" "$role" "$subnets" >>"$NETS_FILE"
    done < <(docker network ls --filter "label=com.docker.compose.project=${project}" --format '{{.Name}}' | sort)
  done
}

has_v6_subnets() { cut -d'|' -f3 "$NETS_FILE" | grep -q ':'; }

# Must run in the main shell (not inside $(...)) so that die() stops the script.
decide_families() {
  FAMILIES="4"
  if has_v6_subnets; then
    command -v ip6tables >/dev/null || die "a guarded network has IPv6 but ip6tables is missing; refusing to leave IPv6 unguarded"
    FAMILIES="4 6"
  elif command -v ip6tables >/dev/null && ip6tables -w -S INPUT >/dev/null 2>&1; then
    FAMILIES="4 6"   # keep the (empty) IPv6 chains reconciled so stale rules disappear
  fi
}

# Docker hooks DOCKER-USER into FORWARD for IPv4 always, and for IPv6 only when
# it manages ip6tables. Without that, guard IPv6 forwarding from FORWARD itself.
fwd_parent() {
  if [ "$1" = 6 ] && ! ip6tables -w -S DOCKER-USER >/dev/null 2>&1; then echo FORWARD; else echo DOCKER-USER; fi
}

# desired FAMILY CHAIN: one rule per line — the arguments that follow "-A CHAIN".
desired() {
  local fam=$1 chain=$2 special name role subnets s d
  if [ "$chain" = "$DROP" ]; then
    echo "-m limit --limit 10/min --limit-burst 20 -j LOG --log-prefix bv-guard-drop: --log-level 4"
    echo "-j DROP"
    return
  fi
  if [ "$fam" = 4 ]; then special=$SPECIAL_V4; else special=$SPECIAL_V6; fi
  while IFS='|' read -r name role subnets; do
    for s in $subnets; do
      if [ "$fam" = 4 ] && [[ "$s" == *:* ]]; then continue; fi
      if [ "$fam" = 6 ] && [[ "$s" != *:* ]]; then continue; fi
      echo "-s $s -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN"
      if [ "$chain" = "$IN" ]; then
        echo "-s $s -j $DROP"
        continue
      fi
      echo "-s $s -d $s -j RETURN"
      for d in $special; do echo "-s $s -d $d -j $DROP"; done
      case "$role" in
        internal|lan) echo "-s $s -j $DROP" ;;
        tunnel)
          echo "-s $s -p tcp -m multiport --dports 443,7844 -j RETURN"
          echo "-s $s -p udp -m udp --dport 7844 -j RETURN"
          echo "-s $s -j $DROP"
          ;;
        egress)
          echo "-s $s -p tcp -m tcp --dport 443 -j RETURN"
          echo "-s $s -j $DROP"
          ;;
        transitional) ;;
      esac
    done
  done <"$NETS_FILE"
}

# fill_chain FAMILY TARGET POLICY_CHAIN: append POLICY_CHAIN's desired rules to TARGET.
fill_chain() {
  local fam=$1 target=$2 policy=$3 line
  local -a args
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    read -ra args <<<"$line"
    ipt "$fam" -A "$target" "${args[@]}"
  done < <(desired "$fam" "$policy")
}

current_rules() { ipt "$1" -S "$2" 2>/dev/null || true; }

# The desired chain in iptables' own canonical form, built in a scratch chain
# and renamed, so the comparison with current_rules is exact.
desired_rules() {
  local fam=$1 chain=$2 tmp="${2}-NEW"
  ipt "$fam" -N "$tmp" 2>/dev/null || ipt "$fam" -F "$tmp"
  fill_chain "$fam" "$tmp" "$chain"
  ipt "$fam" -S "$tmp" | sed -E "s/^(-[NA]) ${tmp}( |\$)/\1 ${chain}\2/"
  ipt "$fam" -F "$tmp"
  ipt "$fam" -X "$tmp"
}

reconcile_chain() {
  local fam=$1 chain=$2
  if [ "$(current_rules "$fam" "$chain")" = "$(desired_rules "$fam" "$chain")" ]; then return 0; fi
  ipt "$fam" -N "$chain" 2>/dev/null || ipt "$fam" -F "$chain"
  fill_chain "$fam" "$chain" "$chain"
  note "repaired IPv$fam chain $chain"
  CHANGED=1
}

jump_is_first_and_only() {
  local fam=$1 parent=$2 chain=$3 rules first count
  rules=$(ipt "$fam" -S "$parent")
  first=$(grep -m1 '^-A ' <<<"$rules" || true)
  count=$(grep -c -- "^-A ${parent} -j ${chain}\$" <<<"$rules" || true)
  [ "$first" = "-A ${parent} -j ${chain}" ] && [ "$count" = 1 ]
}

reconcile_jump() {
  local fam=$1 parent=$2 chain=$3
  if jump_is_first_and_only "$fam" "$parent" "$chain"; then return 0; fi
  while ipt "$fam" -D "$parent" -j "$chain" 2>/dev/null; do :; done
  ipt "$fam" -I "$parent" 1 -j "$chain"
  note "repaired IPv$fam jump $parent -> $chain"
  CHANGED=1
}

cmd_apply() {
  require_root
  require_tools
  wait_for_docker
  check_location
  load_networks
  decide_families
  local fam
  for fam in $FAMILIES; do
    reconcile_chain "$fam" "$DROP"
    reconcile_chain "$fam" "$FWD"
    reconcile_chain "$fam" "$IN"
    reconcile_jump "$fam" "$(fwd_parent "$fam")" "$FWD"
    reconcile_jump "$fam" INPUT "$IN"
  done
  if [ "$CHANGED" -eq 0 ]; then say "rules in place, nothing to change"; fi
}

cmd_check() {
  require_root
  require_tools
  WAIT=0
  wait_for_docker
  check_location
  load_networks
  decide_families
  local fam chain drift=0
  for fam in $FAMILIES; do
    if ! ipt "$fam" -S "$DROP" >/dev/null 2>&1; then
      note "drift: IPv$fam chain $DROP missing"
      drift=1
      continue
    fi
    for chain in "$DROP" "$FWD" "$IN"; do
      if [ "$(current_rules "$fam" "$chain")" != "$(desired_rules "$fam" "$chain")" ]; then
        note "drift: IPv$fam chain $chain differs from policy"
        drift=1
      fi
    done
    jump_is_first_and_only "$fam" "$(fwd_parent "$fam")" "$FWD" || { note "drift: IPv$fam $(fwd_parent "$fam") does not start with -j $FWD"; drift=1; }
    jump_is_first_and_only "$fam" INPUT "$IN" || { note "drift: IPv$fam INPUT does not start with -j $IN"; drift=1; }
  done
  if [ "$drift" -eq 1 ]; then exit 1; fi
  say "rules in place"
}

cmd_status() {
  require_root
  local fam chain
  for fam in 4 6; do
    if [ "$fam" = 6 ] && ! command -v ip6tables >/dev/null; then continue; fi
    echo "== IPv$fam"
    { ipt "$fam" -S "$(fwd_parent "$fam")" 2>/dev/null | head -3; } || true
    { ipt "$fam" -S INPUT 2>/dev/null | head -3; } || true
    for chain in "$FWD" "$IN" "$DROP"; do
      ipt "$fam" -S "$chain" 2>/dev/null || echo "(no $chain)"
    done
  done
}

cmd_roles() {
  require_tools
  load_networks
  cut -d'|' -f1,2 "$NETS_FILE" | tr '|' ' '
}

cmd_remove() {
  require_root
  local fam chain parent
  for fam in 4 6; do
    if [ "$fam" = 6 ] && ! command -v ip6tables >/dev/null; then continue; fi
    for parent in DOCKER-USER FORWARD; do
      while ipt "$fam" -D "$parent" -j "$FWD" 2>/dev/null; do :; done
    done
    while ipt "$fam" -D INPUT -j "$IN" 2>/dev/null; do :; done
    for chain in "$FWD" "$IN" "${FWD}-NEW" "${IN}-NEW" "$DROP" "${DROP}-NEW"; do
      ipt "$fam" -F "$chain" 2>/dev/null || true
    done
    for chain in "$FWD" "$IN" "${FWD}-NEW" "${IN}-NEW" "$DROP" "${DROP}-NEW"; do
      ipt "$fam" -X "$chain" 2>/dev/null || true
    done
  done
  say "removed"
}

main() {
  local cmd=${1:-}
  if [ $# -gt 0 ]; then shift; fi
  while [ $# -gt 0 ]; do
    case "$1" in
      --quiet) QUIET=1 ;;
      --wait-for-docker)
        [ $# -ge 2 ] || die "--wait-for-docker needs a number of seconds"
        WAIT=$2
        shift
        ;;
      *) die "unknown option: $1" ;;
    esac
    shift
  done
  case "$WAIT" in ''|*[!0-9]*) die "--wait-for-docker must be a whole number of seconds" ;; esac
  case "$cmd" in
    apply) cmd_apply ;;
    check) cmd_check ;;
    status) cmd_status ;;
    roles) cmd_roles ;;
    remove) cmd_remove ;;
    *) usage ;;
  esac
}

main "$@"
