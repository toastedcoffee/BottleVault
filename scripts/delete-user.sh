#!/usr/bin/env bash
# delete-user.sh — Permanently delete a BottleVault user and all their bottles
#
# Usage:
#   ./delete-user.sh <email>
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

set -euo pipefail

EMAIL="${1:-}"
if [[ -z "$EMAIL" ]]; then
  echo "Usage: $0 <email>" >&2
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

# Escape single quotes in the email so it's safe to embed in the SELECT count queries below.
# The final DELETE uses psql's -v variable substitution (injection-safe), but the preview
# counts are just for display and we want to bail out early if the user doesn't exist.
EMAIL_SQL=$(printf '%s' "$EMAIL" | sed "s/'/''/g")

# Verify the user exists and show how much will be deleted
USER_COUNT=$(docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT COUNT(*) FROM users WHERE email = '$EMAIL_SQL';")

if [[ "$USER_COUNT" != "1" ]]; then
  echo "No user found with email: $EMAIL" >&2
  exit 1
fi

BOTTLE_COUNT=$(docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT COUNT(*) FROM bottles b JOIN users u ON b.user_id = u.id WHERE u.email = '$EMAIL_SQL';")

echo "This will permanently delete:"
echo "  - User: $EMAIL"
echo "  - Bottles owned by this user: $BOTTLE_COUNT"
echo
echo "This cannot be undone."
echo

# Destructive-confirm: require the user to retype the email, not just 'y'
read -rp "Type the email to confirm deletion: " CONFIRM
if [[ "$CONFIRM" != "$EMAIL" ]]; then
  echo "Email did not match. Aborting." >&2
  exit 1
fi

# Delete using psql variable substitution (safe against injection).
# NOTE: `:'var'` interpolation works when SQL is piped on stdin, not via -c.
# (Learned that lesson with reset-password.sh.)
docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  -v "email=$EMAIL" <<'SQL' > /dev/null
DELETE FROM users WHERE email = :'email';
SQL

echo "Deleted user $EMAIL and $BOTTLE_COUNT associated bottle(s)."
