#!/usr/bin/env bash
#
# Snapshot the BottleVault Postgres dataset before a migration.
#
# A ZFS snapshot of a single dataset holding all of PGDATA (pg_wal included) is
# atomic and crash-consistent. Rolling one back looks, to Postgres, exactly like
# recovering from a power loss — which it is built to do. That makes this a
# legitimate instant-rollback mechanism, not a hack.
#
# TWO THINGS MAKE IT NOT WORK:
#   1. PGDATA sharing a dataset with anything else (you'd roll back the rest too).
#   2. pg_wal relocated to a separate dataset — snapshots of two datasets are not
#      atomic with respect to each other, and the result is not recoverable.
#
# Usage:  ./pg-snapshot.sh <pool>/<dataset> [keep]
# Example: ./pg-snapshot.sh tank/appdata/bottlevault-pg 10
#
# Run this on the TrueNAS host, as root, BEFORE `docker compose pull && up -d`
# for any release carrying a new file under backend/src/main/resources/db/migration/.

set -euo pipefail

DATASET="${1:-}"
KEEP="${2:-10}"
PREFIX="bv-predeploy"

if [ -z "$DATASET" ]; then
    echo "usage: $0 <pool>/<dataset> [keep]" >&2
    exit 2
fi

case "$KEEP" in
    ''|*[!0-9]*)
        echo "error: keep must be a positive integer (got: $KEEP)" >&2
        exit 2
        ;;
esac
if [ "$KEEP" -lt 1 ]; then
    echo "error: keep must be at least 1 — 0 would prune the snapshot just created" >&2
    exit 2
fi

if ! zfs list -H -o name "$DATASET" >/dev/null 2>&1; then
    echo "error: no such dataset: $DATASET" >&2
    echo "hint:  zfs list -o name | grep -i bottle" >&2
    exit 1
fi

# Guard against the most likely misconfiguration: pointing this at a parent
# dataset that holds more than the database. A dataset with child datasets is
# almost certainly not a dedicated PGDATA dataset.
CHILDREN="$(zfs list -H -r -d 1 -o name "$DATASET" | tail -n +2 | wc -l)"
if [ "$CHILDREN" -gt 0 ]; then
    echo "error: $DATASET has $CHILDREN child dataset(s) — this does not look like a" >&2
    echo "       dedicated PGDATA dataset. Rolling it back would roll back the children." >&2
    exit 1
fi

SNAPSHOT="${DATASET}@${PREFIX}-$(date -u +%Y%m%dT%H%M%SZ)"

echo "==> creating $SNAPSHOT"
zfs snapshot "$SNAPSHOT"

echo "==> pruning ${PREFIX} snapshots, keeping newest $KEEP"
# -s creation sorts oldest-first, so head -n -KEEP is everything but the newest.
# grep matches nothing on a first run (no prior ${PREFIX} snapshots) and exits 1,
# which would otherwise fail the whole pipeline under pipefail and kill the script
# — via set -e — before it ever reaches the rollback instructions below. That's an
# expected, non-fatal case, so it's tolerated here the same way the summary grep
# a few lines down already tolerates a no-match result.
zfs list -H -t snapshot -o name -s creation -r "$DATASET" \
    | grep -F "@${PREFIX}-" \
    | head -n "-${KEEP}" \
    | while read -r old; do
        echo "    destroying $old"
        zfs destroy "$old"
    done || true

echo
echo "==> current ${PREFIX} snapshots:"
zfs list -t snapshot -o name,used,creation -s creation -r "$DATASET" | grep -F "@${PREFIX}-" || true

cat <<EOF

Done. To roll back after a bad migration:

  1. Stop the stack in Dockge (or: docker compose -f docker-compose.prod.yml down)
  2. zfs rollback -r $SNAPSHOT
  3. Repoint the stack at the PREVIOUS image tag — rolling back the data without
     rolling back the code leaves new code running against an old schema.
  4. Start the stack, then verify: docker logs bottlevault-api | grep -i flyway
EOF
