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
#   BV_ALLOWED_DNS     space-separated resolver addresses (IPv4 or IPv6, single
#                      addresses only) that tunnel, egress and transitional
#                      networks may query on udp/tcp 53. Docker's embedded DNS
#                      forwards internet lookups from inside the container, so
#                      they leave through the guard like any other packet.
#                      Empty or unset: no resolver is allowed (fails closed):
#                      tunnel and egress networks get no DNS at all, and
#                      transitional ones only what their public allowance
#                      already reaches. Every run says so on stderr, --quiet
#                      or not. (Transitional networks reach any public address
#                      on any port, port 53 included, list or no list.)
#   BV_GUARD_SKIP_LOCATION_CHECK=1   test harness only; never on a real host
#
# Policy for traffic that ENTERS THE HOST FROM a guarded network's bridge
# (whatever source address it claims: link-local and forged ones included):
#   replies to connections opened from outside ........ allowed
#   IPv6 neighbour discovery to the host ............... allowed
#   traffic to the same network ........................ allowed
#   DNS to a BV_ALLOWED_DNS address, from tunnel,
#     egress and transitional networks ................. allowed
#   private and special ranges (LAN, other Docker
#     networks, CGNAT/VPN, link-local, loopback,
#     multicast), and every prefix the host reaches
#     without a gateway (its LANs, whatever their
#     addresses; Docker's own bridges excepted) ........ logged, dropped
#   anything addressed to the host itself ............. logged, dropped
#   everything else, by role:
#     internal      Docker internal network ........... dropped
#     lan           name ends in _lan (exists only to
#                   publish a LAN port) ............... dropped
#     tunnel        name ends in _tunnel_out .......... tcp 443/7844, udp 7844
#     egress        name ends in _egress_out .......... tcp 443
#     transitional  any other non-internal network .... allowed
#   and anything the role allows is allowed only from the network's own
#   subnets; a forged source is dropped
#
# Run as root, from a root-owned directory that is not mounted into any
# container (the script refuses otherwise). Install: DEPLOY.md §9.
set -eEuo pipefail
# $(...) subshells keep errexit, so a failure inside one aborts it instead of
# returning truncated output that is then compared or applied as if complete.
shopt -s inherit_errexit

readonly FWD=BV-GUARD
readonly IN=BV-GUARD-IN
readonly DROP=BV-LOG-DROP
readonly SPECIAL_V4="0.0.0.0/8 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 127.0.0.0/8 192.0.0.0/24 198.18.0.0/15 224.0.0.0/4 240.0.0.0/4"
readonly SPECIAL_V6="fc00::/7 fe80::/10 ff00::/8 ::1/128"
readonly LOCK=/run/bv-egress-guard.lock
readonly LOCK_WAIT=30

PROJECTS="${BV_PROJECTS:-bottlevault}"
QUIET=0
WAIT=0
NETS_FILE=""
FAMILIES="4"
CHANGED=0
DESIRED=""
BRIDGE=""
STRAY=0
DNS_V4=""
DNS_V6=""
ONLINK_V4=""
ONLINK_V6=""

die()  { echo "egress-guard: ERROR: $*" >&2; exit 2; }
say()  { [ "$QUIET" -eq 1 ] || echo "egress-guard: $*"; }
warn() { [ "$QUIET" -eq 1 ] || echo "egress-guard: WARN: $*" >&2; }
loud() { echo "egress-guard: WARN: $*" >&2; }   # always printed: cron mails it
note() { echo "egress-guard: $*"; }   # always printed: repairs and drift

# Exit 1 means "drift" to check's callers, so no unexpected failure may exit
# with a command's own status. set -E carries this trap into functions and
# subshells; conditions (if, while, ||) are exempt, which is what lets the
# script probe with a failing command on purpose.
on_error() {
  echo "egress-guard: ERROR: unexpected failure (exit $1) at line $2: $3" >&2
  exit 2
}
trap 'on_error $? $LINENO "$BASH_COMMAND"' ERR

cleanup_tmp() { if [ -n "$NETS_FILE" ]; then rm -f "$NETS_FILE"; fi; }
trap cleanup_tmp EXIT

ipt() {
  local fam=$1
  shift
  if [ "$fam" = 4 ]; then iptables -w "$@"; else ip6tables -w "$@"; fi
}

# iptr FAMILY: apply the iptables-restore input on stdin as one transaction.
# --noflush leaves every chain alone except those named by a ":CHAIN" line.
iptr() {
  if [ "$1" = 4 ]; then iptables-restore -w --noflush; else ip6tables-restore -w --noflush; fi
}

# Every run that changes or compares chains shares the *-NEW scratch chains, so
# a cron apply and a manual check must not overlap. Bounded wait, then give up:
# the rules already in place stay in force. flock -n in a loop (not flock -w)
# because BusyBox flock has no -w; util-linux (TrueNAS SCALE) has both.
# flock works on any open fd, read-only included, so a lock file other users
# can open would let any local user stall every run: it is root's, mode 600.
# Callers take it after wait_for_docker, so a boot-time wait does not hold it.
take_lock() {
  local deadline=$((SECONDS + LOCK_WAIT)) old_umask
  command -v flock >/dev/null || die "flock not found (util-linux); refusing to run without a lock"
  old_umask=$(umask)
  umask 077
  exec 9>>"$LOCK" || die "cannot open lock file $LOCK"
  umask "$old_umask"
  [ "$(stat -c '%u' "$LOCK")" = 0 ] || die "lock file $LOCK is not owned by root; refusing to use it"
  chmod 600 "$LOCK"   # also tightens a file created by an older version
  until flock -n 9; do
    [ "$SECONDS" -lt "$deadline" ] || die "another egress-guard run still holds the lock $LOCK after ${LOCK_WAIT}s; leaving the current rules alone"
    sleep 1
  done
}

usage() {
  echo "usage: egress-guard.sh apply [--wait-for-docker SECONDS] [--quiet] | check | status | roles | remove" >&2
  exit 2
}

require_root() { [ "$(id -u)" -eq 0 ] || die "must run as root"; }

require_tools() {
  command -v docker >/dev/null || die "docker CLI not found"
  command -v iptables >/dev/null || die "iptables not found"
  command -v iptables-restore >/dev/null || die "iptables-restore not found"
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
  # Any iptables-legacy call creates a legacy filter table if none exists, after
  # which every iptables-nft call warns on stderr (cron mail every minute).
  # /proc/net/ip_tables_names lists this netns's legacy tables and reading it
  # creates nothing, so only ask iptables-legacy when a filter table is there;
  # Docker on the legacy backend always has one.
  if command -v iptables-legacy >/dev/null && grep -qx filter /proc/net/ip_tables_names 2>/dev/null \
    && iptables-legacy -S DOCKER-USER >/dev/null 2>&1; then
    die "Docker's rules are in the legacy iptables backend but 'iptables' is $(iptables -V); refusing to guard the wrong table"
  fi
  die "Docker is not running or its DOCKER-USER chain is missing (waited ${WAIT}s)"
}

# The script runs as root, from cron. If a container could write to it, that
# container could get root on the host at the next run.
check_location() {
  if [ "${BV_GUARD_SKIP_LOCATION_CHECK:-0}" = 1 ]; then return 0; fi
  local self dir p owner mode ids mounts sources vols attempt
  self=$(readlink -f "$0")
  dir=$(dirname "$self")
  # The script, its directory, and every directory above it: whoever can rename
  # a parent can put a script of their own at this path. A sticky parent (like
  # /tmp) is the exception when root owns it: only root may rename root's
  # entries in it.
  p=$self
  while :; do
    owner=$(stat -c '%U' "$p")
    mode=$(stat -c '%a' "$p")
    [ "$owner" = root ] || die "$p must be owned by root (owner: $owner)"
    if (( (8#$mode & 8#022) != 0 )) && { [ "$p" = "$self" ] || [ "$p" = "$dir" ] || (( (8#$mode & 8#1000) == 0 )); }; then
      die "$p must not be group- or world-writable (mode $mode)"
    fi
    [ "$p" != / ] || break
    p=$(dirname "$p")
  done
  # Captured in the main shell: if Docker cannot say what is mounted, the check
  # fails rather than passing with nothing checked. A container removed between
  # the listing and the inspect fails the inspect, so list again, once.
  for attempt in 1 2; do
    ids=$(docker ps -aq) || die "could not list containers to check their mounts"
    [ -n "$ids" ] || return 0
    # One "<type>|<volume name>|<source>" line per mount.
    # shellcheck disable=SC2086  # container IDs are hex, one per word
    if mounts=$(docker inspect --format '{{range .Mounts}}{{.Type}}|{{.Name}}|{{.Source}}{{"\n"}}{{end}}' $ids); then
      break
    fi
    [ "$attempt" = 1 ] || die "could not inspect containers to check their mounts"
  done
  sources=$(cut -d'|' -f3- <<<"$mounts")
  # A named volume's Source is its own _data directory, but a volume can be a
  # bind in disguise (local driver, o=bind, device=/any/host/path), and volume
  # plugins take host paths as options too. So every absolute-path option of
  # every mounted volume is checked like a bind source.
  vols=$(awk -F'|' '$1 == "volume" && $2 != "" { print $2 }' <<<"$mounts" | sort -u)
  if [ -n "$vols" ]; then
    # shellcheck disable=SC2086  # volume names cannot contain whitespace
    sources+=$'\n'$(docker volume inspect --format '{{range .Options}}{{.}}{{"\n"}}{{end}}' $vols) \
      || die "could not inspect volume(s) $(tr '\n' ' ' <<<"$vols")to check where they point"
  fi
  check_mount_sources "$self" "$dir" "$sources"
}

# check_mount_sources SELF DIR SOURCES: die if any line of SOURCES is SELF, or
# is DIR or a parent of it. Lines that are not absolute paths (volume options
# such as "bind" or "tmpfs", NFS ":/export") cannot be host paths; skip them.
check_mount_sources() {
  local self=$1 dir=$2 src
  while IFS= read -r src; do
    [[ "$src" == /* ]] || continue
    src=$(readlink -f "$src" 2>/dev/null || printf '%s' "$src")
    src=${src%/}
    if [ "$src" = "$self" ]; then
      die "$self is itself mounted into a container; a container could rewrite this root-run script"
    fi
    case "$dir/" in
      "$src"/*) die "$dir is inside $src, which is mounted into a container; a container could rewrite this root-run script" ;;
    esac
  done <<<"$3"
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

# bridge_name NETWORK DRIVER ID BRIDGE_OPTION: sets BRIDGE to the Linux bridge
# carrying the network's traffic. A global, not printed, so die() stops the
# script. Rules key on it, not on the subnet: a source address is whatever the
# container writes (link-local, or forged with NET_RAW), the interface a packet
# arrives on is not.
bridge_name() {
  local net=$1 driver=$2 id=$3 br=$4
  [ "$driver" = bridge ] || die "network $net uses the '$driver' driver; only bridge networks can be guarded"
  if [ -z "$br" ]; then br="br-${id:0:12}"; fi   # Docker's own naming when no name is set
  # Rule arguments are split on spaces, and IFNAMSIZ allows 15 characters.
  [[ "$br" =~ ^[A-Za-z0-9_.-]{1,15}$ ]] || die "network $net has an unusable bridge name '$br'"
  # A rule for an interface that does not exist matches nothing: refuse it.
  [ -d "/sys/class/net/$br/bridge" ] || die "bridge $br of network $net does not exist or is not a bridge; refusing to guard nothing"
  BRIDGE=$br
}

# Writes "name|role|bridge|subnet subnet ..." lines, sorted by name so rule order is stable.
# Docker's answers are captured in the main shell with || die: a failed query
# must stop the run, because an empty answer would reconcile the guard's chains
# to empty. Zero networks from a query that succeeded is real (stack down).
load_networks() {
  NETS_FILE=$(mktemp)
  local project names net info internal driver id bropt br subnets role
  for project in $PROJECTS; do
    names=$(docker network ls --filter "label=com.docker.compose.project=${project}" --format '{{.Name}}') \
      || die "could not list the networks of project $project; leaving the current rules alone"
    if [ -z "$names" ]; then
      loud "project $project has no networks, so none of it is guarded (stack down, or a wrong name in BV_PROJECTS?)"
    fi
    names=$(sort <<<"$names")
    while IFS= read -r net; do
      [ -n "$net" ] || continue
      info=$(docker network inspect "$net" --format \
        '{{.Internal}}|{{.Driver}}|{{.Id}}|{{with index .Options "com.docker.network.bridge.name"}}{{.}}{{end}}|{{range .IPAM.Config}}{{.Subnet}} {{end}}') \
        || die "could not inspect network $net; leaving the current rules alone"
      IFS='|' read -r internal driver id bropt subnets <<<"$info"
      bridge_name "$net" "$driver" "$id" "$bropt"
      br=$BRIDGE
      role=$(role_of "$net" "$internal")
      case "$role" in
        internal|lan|tunnel|egress|transitional) ;;
        *) die "unknown role '$role' for network $net (check BV_ROLE_OVERRIDES)" ;;
      esac
      if [ "$role" = transitional ]; then
        warn "network $net has no explicit policy; allowing public destinations on any port (transitional)"
      fi
      printf '%s|%s|%s|%s\n' "$net" "$role" "$br" "$subnets" >>"$NETS_FILE"
    done <<<"$names"
  done
}

# Not "cut | grep -q": under pipefail, grep -q exiting early can SIGPIPE cut and
# turn a match into "no IPv6", which would leave IPv6 unguarded.
has_v6_subnets() {
  local subnets
  subnets=$(cut -d'|' -f4 "$NETS_FILE") || die "could not read the network list $NETS_FILE"
  [[ "$subnets" == *:* ]]
}

# Must run in the main shell (not inside $(...)) so that die() stops the script.
decide_families() {
  FAMILIES="4"
  if has_v6_subnets; then
    command -v ip6tables >/dev/null || die "a guarded network has IPv6 but ip6tables is missing; refusing to leave IPv6 unguarded"
    FAMILIES="4 6"
  elif command -v ip6tables >/dev/null && ip6tables -w -S INPUT >/dev/null 2>&1; then
    FAMILIES="4 6"   # keep the (empty) IPv6 chains reconciled so stale rules disappear
  fi
  if [ "$FAMILIES" = "4 6" ]; then
    command -v ip6tables-restore >/dev/null || die "ip6tables-restore not found; refusing to leave IPv6 unguarded"
  fi
}

readonly OCTET='(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])'

# Sets DNS_V4 and DNS_V6 from BV_ALLOWED_DNS, sorted so the rule order does not
# depend on how the list was written. Each entry must be one resolver address a
# container reaches through its bridge: a prefix would open DNS to a whole
# range (a /8 is the LAN), and loopback, link-local, multicast or the host's own
# addresses would never be reached through FORWARD, so lookups would just fail.
load_dns() {
  local a v4="" v6="" route lookers
  for a in ${BV_ALLOWED_DNS:-}; do
    if [[ "$a" =~ ^$OCTET\.$OCTET\.$OCTET\.$OCTET$ ]]; then
      case "$a" in
        0.*|127.*|169.254.*|22[4-9].*|2[3-5][0-9].*)
          die "BV_ALLOWED_DNS entry $a is not a resolver a container can reach (unspecified, loopback, link-local, multicast or reserved)" ;;
      esac
      v4+="$a"$'\n'
    elif [[ "$a" == *:* && "$a" =~ ^[0-9A-Fa-f:]+$ ]]; then
      case "${a,,}" in
        ::|::1|fe[89ab]*|ff*)
          die "BV_ALLOWED_DNS entry $a is not a resolver a container can reach (unspecified, loopback, link-local or multicast)" ;;
      esac
      v6+="$a"$'\n'
    else
      die "BV_ALLOWED_DNS entry '$a' is not a single IPv4 or IPv6 address (no prefixes, no ports)"
    fi
    route=$(ip route get "$a" 2>/dev/null || true)
    if [[ "$route" == local* ]]; then
      die "BV_ALLOWED_DNS entry $a is an address of this host; the guard never opens the host to containers"
    fi
  done
  DNS_V4=$(sort -u <<<"$v4" | tr '\n' ' ')
  DNS_V6=$(sort -u <<<"$v6" | tr '\n' ' ')
  if [ -z "${DNS_V4// /}${DNS_V6// /}" ]; then
    lookers=$(awk -F'|' '$2 == "tunnel" || $2 == "egress" || $2 == "transitional" { printf "%s ", $1 }' "$NETS_FILE") \
      || die "could not read the network list $NETS_FILE"
    if [ -n "$lookers" ]; then
      loud "BV_ALLOWED_DNS is empty, so no resolver is allowed for ${lookers}(failing closed: tunnel and egress networks cannot resolve internet names, transitional ones only through a public resolver); set it to the resolver(s) Docker forwards to"
    fi
  fi
}

# Sets ONLINK_V4 and ONLINK_V6 to the prefixes the host reaches without a
# gateway: its LANs. The special ranges miss a LAN numbered from global space,
# which every IPv6 LAN with an ISP prefix is. Docker's own bridges are left
# out: their subnets come and go with every stack on the host, and each change
# would read as drift. Listed again (once) if a network vanishes mid-inspect.
load_onlink() {
  local ids info skip="lo" id opt fam routes attempt
  for attempt in 1 2; do
    ids=$(docker network ls --filter driver=bridge --format '{{.ID}}') \
      || die "could not list Docker's bridge networks"
    info=""
    [ -n "$ids" ] || break
    # shellcheck disable=SC2086  # network IDs are hex, one per word
    if info=$(docker network inspect --format '{{.Id}}|{{with index .Options "com.docker.network.bridge.name"}}{{.}}{{end}}' $ids); then
      break
    fi
    [ "$attempt" = 1 ] || die "could not inspect Docker's bridge networks"
  done
  while IFS='|' read -r id opt; do
    [ -n "$id" ] || continue
    if [ -n "$opt" ]; then skip+=" $opt"; else skip+=" br-${id:0:12}"; fi
  done <<<"$info"
  for fam in 4 6; do
    # An empty list would quietly leave the LAN open; only a kernel without
    # IPv6 may have no IPv6 routes to read.
    if ! routes=$(ip -"$fam" -o route show table main); then
      { [ "$fam" = 6 ] && [ ! -e /proc/net/if_inet6 ]; } || die "could not read the host's IPv$fam routes"
      routes=""
    fi
    # First field a prefix (not "default", "unreachable", ...), no gateway, not
    # on a skipped device, and not link-local or multicast (already special).
    routes=$(awk -v skip="$skip" '
      BEGIN { n = split(skip, s, " "); for (i = 1; i <= n; i++) skipdev[s[i]] = 1 }
      / via / { next }
      $1 !~ /[.:]/ || $1 !~ /^[0-9A-Fa-f:.]+(\/[0-9]+)?$/ { next }
      tolower($1) ~ /^(fe[89ab]|ff)/ { next }
      { dev = ""; for (i = 2; i < NF; i++) if ($i == "dev") dev = $(i + 1)
        if (!(dev in skipdev)) print $1 }' <<<"$routes" | sort -u | tr '\n' ' ')
    if [ "$fam" = 4 ]; then ONLINK_V4=$routes; else ONLINK_V6=$routes; fi
  done
}

# FORWARD must hand traffic to DOCKER-USER, or rules there see nothing. That
# jump is Docker's, not the guard's to repair, so refuse on every run.
require_forward_hook() {
  local fam=$1 rules
  [ "$(fwd_parent "$fam")" = DOCKER-USER ] || return 0
  rules=$(ipt "$fam" -S FORWARD) || die "could not read IPv$fam chain FORWARD"
  grep -qx -- '-A FORWARD -j DOCKER-USER' <<<"$rules" \
    || die "IPv$fam FORWARD does not jump to DOCKER-USER, so the guard would see no forwarded traffic; restart Docker to restore its chains"
}

# Docker hooks DOCKER-USER into FORWARD for IPv4 always, and for IPv6 only when
# it manages ip6tables. Without that, guard IPv6 forwarding from FORWARD itself.
fwd_parent() {
  if [ "$1" = 6 ] && ! ip6tables -w -S DOCKER-USER >/dev/null 2>&1; then echo FORWARD; else echo DOCKER-USER; fi
}

# desired FAMILY CHAIN: one rule per line — the arguments that follow "-A CHAIN".
desired() {
  local fam=$1 chain=$2 special dns onlink name role br subnets fsubs s d
  if [ "$chain" = "$DROP" ]; then
    echo "-m limit --limit 10/min --limit-burst 20 -j LOG --log-prefix bv-guard-drop: --log-level 4"
    echo "-j DROP"
    return
  fi
  if [ "$fam" = 4 ]; then
    special=$SPECIAL_V4 dns=$DNS_V4 onlink=$ONLINK_V4
  else
    special=$SPECIAL_V6 dns=$DNS_V6 onlink=$ONLINK_V6
  fi
  # Every rule matches the bridge (-i), so no source address, link-local or
  # forged, gets around the policy. Every network gets rules in both families,
  # even one without a subnet in that family: its containers may still have
  # link-local addresses, and with no subnet nothing is allowed out.
  while IFS='|' read -r name role br subnets; do
    echo "-i $br -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN"
    if [ "$chain" = "$IN" ]; then
      if [ "$fam" = 6 ]; then
        # IPv6 needs neighbour discovery with the host (the gateway), and
        # conntrack never marks it ESTABLISHED. Hop limit 255 means it was
        # not routed from anywhere, as RFC 4861 requires of NS and NA.
        echo "-i $br -p ipv6-icmp -m icmp6 --icmpv6-type neighbour-solicitation -m hl --hl-eq 255 -j RETURN"
        echo "-i $br -p ipv6-icmp -m icmp6 --icmpv6-type neighbour-advertisement -m hl --hl-eq 255 -j RETURN"
      fi
      echo "-i $br -j $DROP"
      continue
    fi
    echo "-i $br -o $br -j RETURN"
    # Every allowance names the network's own subnets as source, which is the
    # anti-spoofing check; anything not allowed ends in the drop at the end.
    fsubs=""
    for s in $subnets; do
      if [ "$fam" = 4 ] && [[ "$s" == *:* ]]; then continue; fi
      if [ "$fam" = 6 ] && [[ "$s" != *:* ]]; then continue; fi
      fsubs+=" $s"
    done
    # DNS comes before the drops: the allowed resolver may be private (the LAN
    # router) or on-link.
    case "$role" in
      tunnel|egress|transitional)
        for s in $fsubs; do
          for d in $dns; do
            echo "-i $br -s $s -d $d -p udp -m udp --dport 53 -j RETURN"
            echo "-i $br -s $s -d $d -p tcp -m tcp --dport 53 -j RETURN"
          done
        done
        ;;
    esac
    for d in $special $onlink; do echo "-i $br -d $d -j $DROP"; done
    for s in $fsubs; do
      case "$role" in
        internal|lan) ;;
        tunnel)
          echo "-i $br -s $s -p tcp -m multiport --dports 443,7844 -j RETURN"
          echo "-i $br -s $s -p udp -m udp --dport 7844 -j RETURN"
          ;;
        egress) echo "-i $br -s $s -p tcp -m tcp --dport 443 -j RETURN" ;;
        transitional) echo "-i $br -s $s -j RETURN" ;;
      esac
    done
    echo "-i $br -j $DROP"
  done <"$NETS_FILE"
}

# fill_chain FAMILY TARGET POLICY_CHAIN: replace TARGET's rules with
# POLICY_CHAIN's desired rules in ONE iptables-restore transaction. Under
# --noflush the ":TARGET" line creates TARGET or flushes it, touching no other
# chain; if any rule is rejected, nothing is committed and TARGET keeps its
# previous rules. Rule-by-rule appends could leave it half-filled (fail-open)
# and would repeat that every minute.
fill_chain() {
  local fam=$1 target=$2 policy=$3 rules line block
  # A plain assignment on purpose: "|| die" here would switch errexit off
  # inside desired and let it return a truncated list with status 0.
  rules=$(desired "$fam" "$policy")
  block="*filter"$'\n'":$target - [0:0]"$'\n'
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    block+="-A $target $line"$'\n'
  done <<<"$rules"
  block+="COMMIT"$'\n'
  iptr "$fam" <<<"$block" || die "IPv$fam rules for $target were rejected; $target keeps its previous rules"
}

current_rules() { ipt "$1" -S "$2" 2>/dev/null || true; }

# Sets DESIRED to the chain in iptables' own canonical form, built in a scratch
# chain and renamed, so the comparison with current_rules is exact. Sets a
# global rather than printing: inside $(...) a failure would only end the
# subshell, and a truncated "desired" set would pass for the real one.
desired_rules() {
  local fam=$1 chain=$2 tmp="${2}-NEW" raw
  fill_chain "$fam" "$tmp" "$chain"
  raw=$(ipt "$fam" -S "$tmp") || die "could not read back the scratch chain $tmp"
  ipt "$fam" -F "$tmp"
  ipt "$fam" -X "$tmp"
  DESIRED=$(sed -E "s/^(-[NA]) ${tmp}( |\$)/\1 ${chain}\2/" <<<"$raw")
}

reconcile_chain() {
  local fam=$1 chain=$2 current
  desired_rules "$fam" "$chain"
  current=$(current_rules "$fam" "$chain")
  if [ "$current" = "$DESIRED" ]; then return 0; fi
  fill_chain "$fam" "$chain" "$chain"
  note "repaired IPv$fam chain $chain"
  CHANGED=1
}

jump_is_first_and_only() {
  local fam=$1 parent=$2 chain=$3 rules first count
  # Called as a condition, where errexit is off: without || die, an unreadable
  # parent would look like a missing jump and be reported as drift, not error.
  rules=$(ipt "$fam" -S "$parent") || die "could not read IPv$fam chain $parent"
  first=$(grep -m1 '^-A ' <<<"$rules" || true)
  count=$(grep -c -- "^-A ${parent} -j ${chain}\$" <<<"$rules" || true)
  [ "$first" = "-A ${parent} -j ${chain}" ] && [ "$count" = 1 ]
}

# Delete every existing copy of the jump and insert one at the top in ONE
# iptables-restore transaction, so a failed insert cannot leave the jump
# deleted. Only copies that exist get a -D line: a -D for a missing rule would
# fail the whole transaction. If the parent changes between the read and the
# restore, the restore fails, nothing changes, and the next run retries.
reconcile_jump() {
  local fam=$1 parent=$2 chain=$3 rules count block i
  if jump_is_first_and_only "$fam" "$parent" "$chain"; then return 0; fi
  rules=$(ipt "$fam" -S "$parent") || die "could not read IPv$fam chain $parent"
  count=$(grep -c -- "^-A ${parent} -j ${chain}\$" <<<"$rules" || true)
  block="*filter"$'\n'
  for ((i = 0; i < count; i++)); do block+="-D $parent -j $chain"$'\n'; done
  block+="-I $parent 1 -j $chain"$'\n'"COMMIT"$'\n'
  iptr "$fam" <<<"$block" || die "IPv$fam jump $parent -> $chain could not be repaired; $parent keeps its previous rules"
  note "repaired IPv$fam jump $parent -> $chain"
  CHANGED=1
}

# Sets STRAY to the number of guard jumps in FORWARD when FORWARD is not the
# parent: left by an earlier run, from before Docker managed ip6tables. They
# would run the policy a second time, and remove would be the only cleanup.
count_stray_jumps() {
  local fam=$1 rules
  STRAY=0
  [ "$(fwd_parent "$fam")" = DOCKER-USER ] || return 0
  rules=$(ipt "$fam" -S FORWARD) || die "could not read IPv$fam chain FORWARD"
  STRAY=$(grep -c -- "^-A FORWARD -j ${FWD}\$" <<<"$rules" || true)
}

remove_stray_jumps() {
  local fam=$1 block i
  count_stray_jumps "$fam"
  [ "$STRAY" -gt 0 ] || return 0
  block="*filter"$'\n'
  for ((i = 0; i < STRAY; i++)); do block+="-D FORWARD -j $FWD"$'\n'; done
  block+="COMMIT"$'\n'
  iptr "$fam" <<<"$block" || die "IPv$fam stray jump FORWARD -> $FWD could not be removed"
  note "removed stray IPv$fam jump FORWARD -> $FWD"
  CHANGED=1
}

# Everything apply and check both need before they compare or change a chain.
prepare() {
  local fam
  require_root
  require_tools
  command -v ip >/dev/null || die "ip (iproute2) not found"
  wait_for_docker
  take_lock
  check_location
  load_networks
  decide_families
  load_dns
  load_onlink
  for fam in $FAMILIES; do require_forward_hook "$fam"; done
}

cmd_apply() {
  prepare
  local fam
  for fam in $FAMILIES; do
    reconcile_chain "$fam" "$DROP"
    reconcile_chain "$fam" "$FWD"
    reconcile_chain "$fam" "$IN"
    reconcile_jump "$fam" "$(fwd_parent "$fam")" "$FWD"
    reconcile_jump "$fam" INPUT "$IN"
    remove_stray_jumps "$fam"
  done
  if [ "$CHANGED" -eq 0 ]; then say "rules in place, nothing to change"; fi
}

cmd_check() {
  WAIT=0
  prepare
  local fam chain current drift=0
  for fam in $FAMILIES; do
    if ! ipt "$fam" -S "$DROP" >/dev/null 2>&1; then
      note "drift: IPv$fam chain $DROP missing"
      drift=1
      continue
    fi
    for chain in "$DROP" "$FWD" "$IN"; do
      desired_rules "$fam" "$chain"   # main shell: a failure is exit 2, never "drift"
      current=$(current_rules "$fam" "$chain")
      if [ "$current" != "$DESIRED" ]; then
        note "drift: IPv$fam chain $chain differs from policy"
        drift=1
      fi
    done
    jump_is_first_and_only "$fam" "$(fwd_parent "$fam")" "$FWD" || { note "drift: IPv$fam $(fwd_parent "$fam") does not start with -j $FWD"; drift=1; }
    jump_is_first_and_only "$fam" INPUT "$IN" || { note "drift: IPv$fam INPUT does not start with -j $IN"; drift=1; }
    count_stray_jumps "$fam"
    if [ "$STRAY" -gt 0 ]; then note "drift: IPv$fam FORWARD has a stray -j $FWD"; drift=1; fi
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
  take_lock
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
