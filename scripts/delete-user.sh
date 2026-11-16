#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
# delete-user.sh — Permanently delete a BottleVault user and all their bottles
#
# Usage:
#   ./delete-user.sh <email> [--uploads-dir HOST_PATH]
#
# Shows what will be deleted (user + bottle count), then asks you to retype
# the email to confirm. The bottles.user_id foreign key is ON DELETE CASCADE,
# so deleting the user automatically removes their bottles.
#
# Requirements:
#   - Run on the Docker host (e.g. TrueNAS SCALE shell)
#   - The BottleVault stack must be running (containers: bottlevault-db)
#   - The invoking user needs docker exec access
#
# Use this to clean up test accounts or remove accounts that should no longer
# have access. This is irreversible.
#
# Pass --uploads-dir <host path to the uploads directory> (or set
# UPLOADS_HOST_PATH) to also delete the user's photos, which otherwise stay on
# disk after the account is gone.
#
# Emails are matched the way the app stores them: trimmed and lower-cased.

set -euo pipefail

EMAIL=""
UPLOADS_DIR="${UPLOADS_HOST_PATH:-}"
while [ $# -gt 0 ]; do
  case "$1" in
    --uploads-dir) UPLOADS_DIR="${2:?--uploads-dir needs a path}"; shift ;;
    *) EMAIL="$1" ;;
  esac
  shift
done
if [[ -z "$EMAIL" ]]; then
  echo "Usage: $0 <email> [--uploads-dir HOST_PATH]" >&2
  exit 1
fi
if [[ -n "$UPLOADS_DIR" && ! -d "$UPLOADS_DIR/bottles" ]]; then
  echo "No bottles/ directory under $UPLOADS_DIR; check --uploads-dir. Nothing changed." >&2
  exit 1
fi

DB_CONTAINER="${DB_CONTAINER:-bottlevault-db}"
DB_USER="${DB_USER:-bottlevault}"
DB_NAME="${DB_NAME:-bottlevault}"

# Verify the DB container is running
if ! docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null | grep -q true; then
  echo "Database container '$DB_CONTAINER' is not running." >&2
  echo "Set DB_CONTAINER env var if your container is named differently." >&2
  exit 1
fi

# psql variable substitution (:'var') keeps every value out of the SQL text.
# NOTE: `:'var'` interpolation works when SQL is piped on stdin, not via -c.
psql_exec() { docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qtA "$@"; }

# Verify the user exists and show how much will be deleted
USER_ID=$(psql_exec -v "email=$EMAIL" <<'SQL'
SELECT id FROM users WHERE email = LOWER(TRIM(:'email'));
SQL
)
if [[ -z "$USER_ID" ]]; then
  echo "No user found with email: $EMAIL" >&2
  exit 1
fi

BOTTLE_COUNT=$(psql_exec -v "id=$USER_ID" <<'SQL'
SELECT COUNT(*) FROM bottles WHERE user_id = :'id'::uuid;
SQL
)

echo "This will permanently delete:"
echo "  - User: $EMAIL"
echo "  - Bottles owned by this user: $BOTTLE_COUNT"
if [[ -n "$UPLOADS_DIR" ]]; then
  echo "  - Photos in $UPLOADS_DIR/bottles/$USER_ID"
fi
echo
echo "This cannot be undone."
echo

# Destructive-confirm: require the user to retype the email, not just 'y'
read -rp "Type the email to confirm deletion: " CONFIRM
if [[ "$CONFIRM" != "$EMAIL" ]]; then
  echo "Email did not match. Aborting." >&2
  exit 1
fi

psql_exec -v "id=$USER_ID" <<'SQL' > /dev/null
DELETE FROM users WHERE id = :'id'::uuid;
SQL

echo "Deleted user $EMAIL and $BOTTLE_COUNT associated bottle(s)."

# The id comes from the database; check its shape before it goes anywhere near rm -rf.
if ! printf '%s' "$USER_ID" | grep -Eq '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'; then
  echo "Unexpected user id '$USER_ID'; photos not touched." >&2
elif [[ -n "$UPLOADS_DIR" ]]; then
  rm -rf -- "${UPLOADS_DIR%/}/bottles/$USER_ID"
  echo "Deleted photos in ${UPLOADS_DIR%/}/bottles/$USER_ID"
else
  echo "Photos are still on disk. To delete them: rm -rf -- '<uploads dir>/bottles/$USER_ID'"
fi
