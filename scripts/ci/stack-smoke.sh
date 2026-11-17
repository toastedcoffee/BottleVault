#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# stack-smoke.sh — start docker-compose.prod.yml (without the tunnel) against
# locally built images and assert the stack's security properties with real
# containers. Used by .github/workflows/stack-smoke.yml; also runnable locally
# with Docker (Linux, or Docker Desktop from Git Bash).
#
# Expects these images to exist locally already:
#   ghcr.io/toastedcoffee/bottlevault-api:latest
#   ghcr.io/toastedcoffee/bottlevault-web:latest
set -uo pipefail   # no -e: every check reports, the summary decides
# Git Bash on Windows would otherwise rewrite container paths such as
# /etc/nginx/... into C:/Program Files/Git/etc/nginx/... . Harmless on Linux.
export MSYS_NO_PATHCONV=1
# Checks capture command output first and grep the captured text. Piping into
# `grep -q` would let grep exit at the first match, kill the writer with SIGPIPE,
# and — under pipefail — report a success as a failure.

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
WORK=$(mktemp -d)
PROJECT=bvsmoke
API_IMAGE=ghcr.io/toastedcoffee/bottlevault-api:latest
# Run the stack in its tunnel shape — the way the live deployment runs it: no
# LAN port and no `lan` network on the frontend. (Needs Compose >= 2.24.4 for
# the !reset / !override tags.)
cat >"$WORK/tunnel-shape.yml" <<'EOF'
services:
  frontend:
    ports: !reset []
    networks: !override [internal, edge]
EOF
# With path conversion off, host paths must be given to the native docker CLI in
# Windows form; cygpath exists only under Git Bash / MSYS.
if command -v cygpath >/dev/null 2>&1; then HOST_ROOT=$(cygpath -m "$ROOT"); HOST_WORK=$(cygpath -m "$WORK"); else HOST_ROOT=$ROOT; HOST_WORK=$WORK; fi
COMPOSE=(docker compose -p "$PROJECT" -f "$HOST_ROOT/docker-compose.prod.yml" -f "$HOST_WORK/tunnel-shape.yml" --project-directory "$HOST_WORK")
CURL=curlimages/curl:8.11.1
PASSES=0
FAILS=0

pass() { PASSES=$((PASSES + 1)); echo "PASS  $*"; }
fail() { FAILS=$((FAILS + 1)); echo "FAIL  $*"; }
check() { local desc=$1; shift; if "$@"; then pass "$desc"; else fail "$desc"; fi; }
not() { ! "$@"; }

cleanup() {
  if [ "$FAILS" -gt 0 ]; then "${COMPOSE[@]}" logs --tail 80 2>&1 | sed 's/^/LOG   /'; fi
  "${COMPOSE[@]}" down --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$WORK" 2>/dev/null || sudo rm -rf "$WORK" 2>/dev/null || true
}
trap cleanup EXIT

export DB_PASSWORD=smoke-db-password
export JWT_SECRET=smoke-jwt-secret-long-enough-for-hmac-sha256-0123456789abcdef
export TUNNEL_TOKEN=unused-because-the-tunnel-is-not-started
export IP_HASH_KEY=smoke-ip-hash-key

# The README's first-start step: writable directories owned by 568.
mkdir -p "$WORK/data/uploads" "$WORK/data/logs/nginx" "$WORK/data/logs/api"
chown -R 568:568 "$WORK/data/uploads" "$WORK/data/logs" 2>/dev/null \
  || sudo chown -R 568:568 "$WORK/data/uploads" "$WORK/data/logs" 2>/dev/null \
  || echo "INFO  could not chown (Docker Desktop bind mounts are writable anyway)"

"${COMPOSE[@]}" up -d postgres egress backend frontend || { echo "compose up failed"; exit 1; }

wait_healthy() { # CONTAINER SECONDS
  local deadline=$((SECONDS + $2)) status
  while [ "$SECONDS" -lt "$deadline" ]; do
    status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$1" 2>/dev/null || echo missing)
    [ "$status" = healthy ] && return 0
    sleep 3
  done
  return 1
}
check "postgres healthy" wait_healthy bottlevault-db 120
check "backend healthy" wait_healthy bottlevault-api 300
check "frontend healthy (nginx as 568 on port 80)" wait_healthy bottlevault-web 60

uid_is() { [ "$(docker exec "$1" id -u 2>/dev/null)" = "$2" ]; }
check "api runs as 568" uid_is bottlevault-api 568
check "web runs as 568" uid_is bottlevault-web 568
check "egress does not run as root" not uid_is bottlevault-egress 0

read_only_root() { not docker exec "$1" sh -c 'touch /smoke-probe' >/dev/null 2>&1; }
# The egress proxy is deliberately not read-only on Path A (Compose cannot inject
# an inline config into a read-only container); it is covered by the
# non-root and no-capabilities checks instead.
for c in bottlevault-api bottlevault-web; do
  check "$c: root filesystem is read-only" read_only_root "$c"
done

no_caps() { docker exec "$1" sh -c 'grep -Eq "^CapEff:[[:space:]]+0+$" /proc/1/status'; }
for c in bottlevault-api bottlevault-web bottlevault-egress; do
  check "$c: no effective capabilities" no_caps "$c"
done

no_new_privs() { [[ "$(docker inspect -f '{{json .HostConfig.SecurityOpt}}' "$1")" == *no-new-privileges:true* ]]; }
has_limits() {
  [ "$(docker inspect -f '{{.HostConfig.Memory}}' "$1")" != 0 ] \
    && [ "$(docker inspect -f '{{if .HostConfig.PidsLimit}}{{.HostConfig.PidsLimit}}{{else}}0{{end}}' "$1")" -gt 0 ]
}
for c in bottlevault-db bottlevault-api bottlevault-web bottlevault-egress; do
  check "$c: no-new-privileges" no_new_privs "$c"
  check "$c: memory and PID limits" has_limits "$c"
done
# Checked on the networks: Docker 29 prints an empty container IPv6 field as
# "invalid IP", so the container-level field is not a reliable signal.
ipv6_off() { [ "$(docker network inspect -f '{{.EnableIPv6}}' "$1")" = false ]; }
for n in internal proxy edge egress_out; do
  check "network ${PROJECT}_$n has IPv6 disabled" ipv6_off "${PROJECT}_$n"
done
is_internal() { [ "$(docker network inspect -f '{{.Internal}}' "$1")" = true ]; }
for n in internal proxy edge; do
  check "network ${PROJECT}_$n is internal" is_internal "${PROJECT}_$n"
done
# Tunnel shape: nothing on the host's ports (G8: `docker port bottlevault-web` empty).
no_host_port() { local p; p=$(docker port bottlevault-web 2>&1); [ -z "$p" ]; }
check "web publishes no host port in the tunnel shape" no_host_port

has_wget() { docker exec "$1" sh -c 'command -v wget' >/dev/null 2>&1; }
no_internet() { has_wget "$1" && not docker exec "$1" sh -c 'wget -q -T 5 -O /dev/null https://1.1.1.1/' >/dev/null 2>&1; }
check "postgres cannot reach the internet" no_internet bottlevault-db
check "web cannot reach the internet" no_internet bottlevault-web
check "api cannot reach the internet directly" no_internet bottlevault-api

connect_code() { docker run --rm --network "${PROJECT}_proxy" "$CURL" -s -o /dev/null --max-time 15 -w '%{http_connect}' -x http://egress:3128 "$1"; }
connect_is() { [ "$(connect_code "$1")" = "$2" ]; }
# The one check that needs a third-party site up: retry it before calling it a failure.
connect_ok_retry() { local i; for i in 1 2 3; do connect_is "$1" 200 && return 0; sleep 5; done; return 1; }
check "proxy allows an allowlisted host" connect_ok_retry https://world.openfoodfacts.org/
check "proxy refuses a host not on the allowlist" connect_is https://example.com/ 403
check "proxy refuses a private address" connect_is https://10.1.2.3/ 403
plain_http_refused() { [ "$(docker run --rm --network "${PROJECT}_proxy" "$CURL" -s -o /dev/null --max-time 15 -w '%{http_code}' -x http://egress:3128 http://world.openfoodfacts.org/)" = 403 ]; }
check "proxy refuses plain HTTP" plain_http_refused
# The proxy resolves internet names through the public resolvers set in compose
# (BV_DNS_1/2), never the host's: the host guard's DNS allowance names only those.
egress_dns_public() {
  local dns
  dns=$(docker inspect -f '{{json .HostConfig.Dns}}' bottlevault-egress)
  [[ "$dns" == *'"1.1.1.1"'* && "$dns" == *'"9.9.9.9"'* ]]
}
check "egress proxy uses the public resolvers" egress_dns_public
# A CONNECT to a bare IP whose PTR names an allowed host must not match the
# allowlist; `dstdomain -n` turns that reverse lookup off. Not provable with a
# live PTR record here, so check the delivered config.
no_reverse_lookup() { docker exec bottlevault-egress grep -Eq '^acl allowed_hosts dstdomain -n ' /etc/squid/squid.conf; }
check "proxy allowlist never reverse-resolves IP destinations" no_reverse_lookup

edge_head() { docker run --rm --network "${PROJECT}_edge" "$CURL" -s -I --max-time 10 "http://frontend$1"; }
header_has() {
  local headers line
  headers=$(edge_head "$1")
  line=$(grep -i "^$2:" <<<"$headers")
  [[ "$line" == *"$3"* ]]
}
check "CSP on / allows the Turnstile script" header_has / content-security-policy "script-src 'self' https://challenges.cloudflare.com"
check "CSP on / allows the Turnstile frame" header_has / content-security-policy "frame-src https://challenges.cloudflare.com"
ASSET=$(docker exec bottlevault-web sh -c 'ls /usr/share/nginx/html/assets | grep -m1 "\.js$"')
check "CSP is also sent with static assets" header_has "/assets/$ASSET" content-security-policy "frame-ancestors 'none'"
check "COOP is sent" header_has / cross-origin-opener-policy same-origin
check "CORP is sent" header_has / cross-origin-resource-policy same-origin
no_version() {
  local headers
  headers=$(edge_head /)
  grep -qi '^server: nginx' <<<"$headers" && ! grep -qi '^server: nginx/' <<<"$headers"
}
check "nginx version is not disclosed" no_version

peer_header() { docker exec bottlevault-web grep -q 'proxy_set_header X-BV-Peer-IP \$remote_addr' /etc/nginx/snippets/bv-proxy.conf; }
check "nginx sets X-BV-Peer-IP" peer_header

auth_burst_hits_nginx_limit() {
  local codes
  codes=$(docker run --rm --network "${PROJECT}_edge" --entrypoint sh "$CURL" -c '
    for i in $(seq 1 40); do
      curl -s -o /dev/null -w "%{http_code} %{content_type}\n" -X POST \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"smoke@example.com\",\"password\":\"wrong-password\"}" \
        http://frontend/api/auth/login
    done')
  # nginx's own 429 is an HTML page; the backend's is JSON.
  grep -q '^429 text/html' <<<"$codes"
}
check "nginx rate-limits /api/auth/login per client" auth_burst_hits_nginx_limit

small_body_cap() {
  local code
  code=$(docker run --rm --network "${PROJECT}_edge" --entrypoint sh "$CURL" -c '
    head -c 300000 /dev/zero | tr "\0" a > /tmp/body
    curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" \
      --data-binary @/tmp/body http://frontend/api/bottles')
  [ "$code" = 413 ]
}
check "non-upload API bodies are capped at 256k" small_body_cap

port_sysctl() { [ "$(docker exec bottlevault-web cat /proc/sys/net/ipv4/ip_unprivileged_port_start)" = 0 ]; }
check "A1: containers may bind low ports unprivileged" port_sysctl

writable() { docker exec bottlevault-api sh -c "touch $1/.smoke && rm $1/.smoke"; }
check "api can write /data/uploads" writable /data/uploads
check "api can write /data/logs" writable /data/logs

# One-off tools (e.g. a catalog review script) run the API image with another
# entrypoint and main class, offline and read-only. Keep that possible.
api_image_runs_java_offline() {
  docker run --rm --network none --read-only --cap-drop ALL \
    --security-opt no-new-privileges:true --entrypoint sh "$API_IMAGE" \
    -c 'test "$(id -u)" = 568 && test -r /app/app.jar && java -version' >/dev/null 2>&1
}
check "api image runs java under another entrypoint (offline, read-only)" api_image_runs_java_offline

echo
echo "passes=$PASSES fails=$FAILS"
[ "$FAILS" -eq 0 ]
