#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
# invite.sh — create, list and revoke BottleVault invite codes
#
# Usage:
#   ./invite.sh create [--email ADDRESS] [--uses N] [--days N] [--note TEXT] [--base-url URL]
#   ./invite.sh list [--all]
#   ./invite.sh revoke <id-prefix>
#
# `create` prints the code and a sign-up link ONCE. Only a SHA-256 of the code is
# stored, so a lost code cannot be recovered — revoke it and create another.
# The link carries the code after '#', which browsers never send to the server,
# so codes do not end up in web server logs.
#
# Defaults: one use, valid 14 days, any email. --email binds the code to one
# address (case-insensitive).
#
# Requirements:
#   - Run on the Docker host (e.g. TrueNAS SCALE shell)
#   - The BottleVault stack must be running (container: bottlevault-db)
#   - The invoking user needs docker exec access
#
# Env: DB_CONTAINER (default bottlevault-db), DB_USER, DB_NAME (default
# bottlevault), BV_PUBLIC_URL (default for --base-url).

set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-bottlevault-db}"
DB_USER="${DB_USER:-bottlevault}"
DB_NAME="${DB_NAME:-bottlevault}"
ALPHABET='0123456789ABCDEFGHJKMNPQRSTVWXYZ'

usage() { sed -n '6,9p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }

psql_exec() { docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -tA "$@"; }

cmd="${1:-}"
[ -n "$cmd" ] || usage
shift

if ! docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null | grep -q true; then
  echo "Database container '$DB_CONTAINER' is not running." >&2
  echo "Set DB_CONTAINER env var if your container is named differently." >&2
  exit 1
fi

case "$cmd" in
  create)
    EMAIL="" USES=1 DAYS=14 NOTE="" BASE_URL="${BV_PUBLIC_URL:-}"
    while [ $# -gt 0 ]; do
      case "$1" in
        --email) EMAIL=$(printf '%s' "${2:?}" | tr '[:upper:]' '[:lower:]' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'); shift ;;
        --uses) USES="${2:?}"; shift ;;
        --days) DAYS="${2:?}"; shift ;;
        --note) NOTE="${2:?}"; shift ;;
        --base-url) BASE_URL="${2:?}"; shift ;;
        *) usage ;;
      esac
      shift
    done
    case "$USES" in ''|*[!0-9]*|0) echo "--uses must be a positive whole number" >&2; exit 2 ;; esac
    case "$DAYS" in ''|*[!0-9]*|0) echo "--days must be a positive whole number" >&2; exit 2 ;; esac
    if [ "${#NOTE}" -gt 200 ]; then echo "--note must be at most 200 characters" >&2; exit 2; fi

    # 16 characters from the 32-letter alphabet = 80 bits. Each alphabet letter is
    # exactly one byte value, so keeping only those bytes is uniform. `|| true`
    # absorbs head closing the pipe early (SIGPIPE under pipefail).
    CODE=$(LC_ALL=C tr -dc "$ALPHABET" </dev/urandom 2>/dev/null | head -c 16 || true)
    if [ "${#CODE}" -ne 16 ]; then echo "Could not generate a code." >&2; exit 1; fi
    HASH=$(printf '%s' "$CODE" | sha256sum | cut -d' ' -f1)

    # Values go in as psql variables (:'name' quotes them), never spliced into SQL.
    ID=$(psql_exec -v "hash=$HASH" -v "email=$EMAIL" -v "note=$NOTE" -v "uses=$USES" -v "days=$DAYS" <<'SQL'
INSERT INTO invite_codes (code_hash, email, note, max_uses, expires_at)
VALUES (:'hash', NULLIF(:'email', ''), NULLIF(:'note', ''), :'uses'::int, NOW() + make_interval(days => :'days'::int))
RETURNING id;
SQL
)
    ID=$(printf '%s' "$ID" | head -n 1)
    PRETTY="${CODE:0:4}-${CODE:4:4}-${CODE:8:4}-${CODE:12:4}"
    echo "Invite ${ID:0:8} — ${USES} use(s), valid ${DAYS} day(s)${EMAIL:+, only for $EMAIL}${NOTE:+ ($NOTE)}"
    echo
    echo "  Code: $PRETTY"
    if [ -n "$BASE_URL" ]; then
      echo "  Link: ${BASE_URL%/}/register#invite=$PRETTY"
    else
      echo "  Link: pass --base-url https://your-app-hostname (or set BV_PUBLIC_URL) to get a link"
    fi
    echo
    echo "This is the only time the code is shown."
    ;;

  list)
    FILTER="WHERE revoked_at IS NULL AND expires_at > NOW() AND use_count < max_uses"
    if [ "${1:-}" = "--all" ]; then FILTER=""; fi
    echo "id        uses  expires     status   email / note"
    psql_exec -F ' ' <<SQL
SELECT left(id::text, 8),
       lpad(use_count || '/' || max_uses, 5),
       to_char(expires_at, 'YYYY-MM-DD'),
       rpad(CASE WHEN revoked_at IS NOT NULL THEN 'revoked'
                 WHEN expires_at <= NOW() THEN 'expired'
                 WHEN use_count >= max_uses THEN 'used'
                 ELSE 'active' END, 8),
       concat_ws(' / ', email, note)
FROM invite_codes $FILTER ORDER BY created_at DESC;
SQL
    ;;

  revoke)
    PREFIX="${1:-}"
    if ! printf '%s' "$PREFIX" | grep -Eq '^[0-9a-f-]{4,36}$'; then
      echo "Give the id prefix shown by 'list' (at least 4 characters)." >&2; exit 2
    fi
    MATCHES=$(psql_exec -v "prefix=$PREFIX" <<'SQL'
SELECT COUNT(*) FROM invite_codes WHERE id::text LIKE :'prefix' || '%' AND revoked_at IS NULL;
SQL
)
    if [ "$MATCHES" != "1" ]; then
      echo "That prefix matches $MATCHES unrevoked invites; give more characters." >&2; exit 1
    fi
    psql_exec -v "prefix=$PREFIX" <<'SQL' >/dev/null
UPDATE invite_codes SET revoked_at = NOW() WHERE id::text LIKE :'prefix' || '%' AND revoked_at IS NULL;
SQL
    echo "Revoked invite $PREFIX. Accounts already created with it are not affected."
    ;;

  *) usage ;;
esac
