#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
# suspend-user.sh — suspend or restore a BottleVault account
#
# Usage:
#   ./suspend-user.sh <email> [--reason TEXT]
#   ./suspend-user.sh --undo <email>
#
# Suspending blocks sign-in and token refresh and deletes every refresh session;
# the account's access tokens stop working within a minute. Nothing is deleted —
# --undo restores the account as it was (the user signs in again).
#
# Requirements: run on the Docker host; the stack must be running; docker exec access.
# Env: DB_CONTAINER (default bottlevault-db), DB_USER, DB_NAME (default bottlevault).

set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-bottlevault-db}"
DB_USER="${DB_USER:-bottlevault}"
DB_NAME="${DB_NAME:-bottlevault}"

psql_exec() { docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qtA "$@"; }

UNDO=0 EMAIL="" REASON=""
while [ $# -gt 0 ]; do
  case "$1" in
    --undo) UNDO=1 ;;
    --reason) REASON="${2:?--reason needs text}"; shift ;;
    -*) echo "Unknown option: $1" >&2; exit 2 ;;
    *) EMAIL="$1" ;;
  esac
  shift
done
[ -n "$EMAIL" ] || { sed -n '6,8p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }
if [ "${#REASON}" -gt 200 ]; then echo "--reason must be at most 200 characters" >&2; exit 2; fi

if ! docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null | grep -q true; then
  echo "Database container '$DB_CONTAINER' is not running." >&2
  exit 1
fi

# Values go in as psql variables (:'name' quotes them), never spliced into SQL.
if [ "$UNDO" -eq 1 ]; then
  ID=$(psql_exec -v "email=$EMAIL" <<'SQL'
UPDATE users SET disabled_at = NULL, disabled_reason = NULL, updated_at = NOW()
WHERE email = LOWER(TRIM(:'email')) RETURNING id;
SQL
)
  [ -n "$ID" ] || { echo "No user found with email: $EMAIL" >&2; exit 1; }
  echo "Restored $EMAIL. They can sign in again."
  exit 0
fi

# One transaction: the flag and the session purge land together or not at all.
ID=$(psql_exec -v "email=$EMAIL" -v "reason=$REASON" <<'SQL'
BEGIN;
WITH target AS (
  UPDATE users SET disabled_at = NOW(), disabled_reason = NULLIF(:'reason', ''), updated_at = NOW()
  WHERE email = LOWER(TRIM(:'email')) RETURNING id
), gone AS (
  DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM target)
)
SELECT id FROM target;
COMMIT;
SQL
)
[ -n "$ID" ] || { echo "No user found with email: $EMAIL" >&2; exit 1; }
echo "Suspended $EMAIL (${ID:0:8}). All sessions ended; open tabs lose access within a minute."
