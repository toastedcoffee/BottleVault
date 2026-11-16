#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
# revoke-sessions.sh — end BottleVault sign-in sessions
#
# Usage:
#   ./revoke-sessions.sh <email>     end one account's sessions
#   ./revoke-sessions.sh --all       end EVERY account's sessions
#
# Deletes refresh sessions, so affected users must sign in again once their
# current access token expires (at most 15 minutes). To make --all immediate,
# also rotate JWT_SECRET in the stack's environment and redeploy the API: that
# invalidates every access token at once.
#
# Requirements: run on the Docker host; the stack must be running; docker exec access.
# Env: DB_CONTAINER (default bottlevault-db), DB_USER, DB_NAME (default bottlevault).

set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-bottlevault-db}"
DB_USER="${DB_USER:-bottlevault}"
DB_NAME="${DB_NAME:-bottlevault}"

psql_exec() { docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qtA "$@"; }

TARGET="${1:-}"
[ -n "$TARGET" ] || { sed -n '6,8p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }

if ! docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null | grep -q true; then
  echo "Database container '$DB_CONTAINER' is not running." >&2
  exit 1
fi

if [ "$TARGET" = "--all" ]; then
  COUNT=$(psql_exec <<'SQL'
SELECT COUNT(*) FROM refresh_tokens;
SQL
)
  echo "This ends all $COUNT sessions for every account."
  read -rp "Type ALL SESSIONS to confirm: " CONFIRM
  [ "$CONFIRM" = "ALL SESSIONS" ] || { echo "Not confirmed. Nothing changed." >&2; exit 1; }
  psql_exec <<'SQL' >/dev/null
DELETE FROM refresh_tokens;
SQL
  echo "Ended all sessions. Rotate JWT_SECRET and redeploy the API to cut off access tokens now."
  exit 0
fi

FOUND=$(psql_exec -v "email=$TARGET" <<'SQL'
SELECT COUNT(*) FROM users WHERE email = LOWER(TRIM(:'email'));
SQL
)
[ "$FOUND" = "1" ] || { echo "No user found with email: $TARGET" >&2; exit 1; }

COUNT=$(psql_exec -v "email=$TARGET" <<'SQL'
WITH gone AS (
  DELETE FROM refresh_tokens
  WHERE user_id = (SELECT id FROM users WHERE email = LOWER(TRIM(:'email')))
  RETURNING 1
)
SELECT COUNT(*) FROM gone;
SQL
)
echo "Ended $COUNT session(s) for $TARGET. Their open tabs sign out within 15 minutes."
