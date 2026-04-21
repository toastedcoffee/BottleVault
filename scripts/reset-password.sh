#!/usr/bin/env bash
# reset-password.sh — Emergency password reset for BottleVault
#
# Usage:
#   ./reset-password.sh <email>
#
# Prompts for a new password (twice), generates a BCrypt hash using a throwaway
# httpd:alpine container, and updates the users table directly via docker exec.
#
# Requirements:
#   - Run on the Docker host (e.g. TrueNAS SCALE shell, not inside Dockge)
#   - The BottleVault stack must be running (containers: bottlevault-db, bottlevault-api)
#   - The invoking user needs docker exec access
#
# Use this when someone is locked out of their account. For everyday password
# changes, use the Settings page in the app.

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

# Verify the user exists before asking for a password
EXISTS=$(docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT COUNT(*) FROM users WHERE email = '$(printf '%s' "$EMAIL" | sed "s/'/''/g")';")

if [[ "$EXISTS" != "1" ]]; then
  echo "No user found with email: $EMAIL" >&2
  exit 1
fi

# Prompt for the new password (silent)
read -rsp "New password for $EMAIL: " PASSWORD
echo
read -rsp "Confirm password: " PASSWORD_CONFIRM
echo

if [[ "$PASSWORD" != "$PASSWORD_CONFIRM" ]]; then
  echo "Passwords do not match." >&2
  exit 1
fi

if [[ ${#PASSWORD} -lt 8 ]]; then
  echo "Password must be at least 8 characters." >&2
  exit 1
fi

# Generate a BCrypt hash (cost 10, matching Spring's BCryptPasswordEncoder default)
# htpasswd writes $2y$ prefix; Spring verifies it fine but normalize to $2a$ for
# consistency with hashes the app generates itself.
HASH=$(docker run --rm httpd:alpine htpasswd -bnBC 10 "" "$PASSWORD" \
  | tr -d ':\n' \
  | sed 's/^\$2y/\$2a/')

if [[ -z "$HASH" || "$HASH" != \$2a\$10\$* ]]; then
  echo "Failed to generate BCrypt hash." >&2
  exit 1
fi

# Update the password using psql variable substitution (safe against injection)
docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  -v "email=$EMAIL" -v "hash=$HASH" \
  -c "UPDATE users SET password_hash = :'hash', updated_at = NOW() WHERE email = :'email';" \
  > /dev/null

echo "Password reset successfully for $EMAIL."
echo "All existing sessions for this user remain valid until their JWT expires."
