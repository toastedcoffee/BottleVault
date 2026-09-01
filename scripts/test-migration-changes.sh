#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# Self-test for scripts/check-migration-changes.sh.
#
# The classifier is a safety rail whose entire behaviour lives in two path
# regexes and a case statement, all of which fail *silently* when wrong -- a
# gate that classifies everything as clean looks exactly like a gate with
# nothing to report. So this runs on every PR (see
# .github/workflows/migration-gate.yml), not just when someone remembers.
#
# Records are built with printf '%s\t%s\t%s' rather than literal tabs, so an
# editor that helpfully converts tabs to spaces cannot quietly break the suite.
#
# The assertions here were mutation-verified when written, not just observed
# green. Each of these deliberate breakages turns at least one test red:
# dropping the Kotlin path from the added-file match, treating a Kotlin edit as
# checksummed, judging renames on the new path instead of the old one,
# narrowing `.+` to `[^/]+` so subdirectories stop matching, ignoring the
# `removed` status, ignoring the override label, removing either exit-2 guard,
# and reverting the clean-vs-backup split in the report copy.
#
# Usage:  bash scripts/test-migration-changes.sh
# Exit:   0 all assertions passed, 1 one or more failed

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SUT="$here/check-migration-changes.sh"

if [ ! -f "$SUT" ]; then
  echo "ERROR: cannot find check-migration-changes.sh next to this script." >&2
  exit 2
fi

SQLDIR="backend/src/main/resources/db/migration"
KTDIR="backend/src/main/kotlin/db/migration"

pass=0
fail=0

# rec <status> <filename> [previous_filename]
rec() { printf '%s\t%s\t%s\n' "$1" "$2" "${3:-}"; }

run_sut() { # run_sut <override> <input>
  printf '%s' "$2" | OVERRIDE_ACTIVE="$1" bash "$SUT" 2>&1
}

# want <name> <expected verdict, or EXIT2> <input> [override]
want() {
  local name="$1" expect="$2" input="$3" override="${4:-0}" out rc verdict
  out="$(run_sut "$override" "$input")"
  rc=$?
  if [ "$expect" = "EXIT2" ]; then
    if [ "$rc" -eq 2 ]; then
      pass=$((pass + 1)); printf '  ok    %s\n' "$name"
    else
      fail=$((fail + 1)); printf '  FAIL  %s -- expected exit 2, got %s\n' "$name" "$rc"
    fi
    return
  fi
  if [ "$rc" -ne 0 ]; then
    fail=$((fail + 1)); printf '  FAIL  %s -- unexpected exit %s\n' "$name" "$rc"
    printf '%s\n' "$out" | sed 's/^/          /'
    return
  fi
  verdict="$(printf '%s' "$out" | sed -n '1p')"
  if [ "$verdict" = "VERDICT=$expect" ]; then
    pass=$((pass + 1)); printf '  ok    %s -> %s\n' "$name" "$expect"
  else
    fail=$((fail + 1)); printf '  FAIL  %s -- expected VERDICT=%s, got %s\n' "$name" "$expect" "$verdict"
  fi
}

# body <name> <needle> <input> [override]
body() {
  local name="$1" needle="$2" input="$3" override="${4:-0}" out
  out="$(run_sut "$override" "$input")"
  if printf '%s' "$out" | grep -qF -- "$needle"; then
    pass=$((pass + 1)); printf '  ok    %s\n' "$name"
  else
    fail=$((fail + 1)); printf '  FAIL  %s -- report is missing %s\n' "$name" "$needle"
  fi
}

# nobody <name> <needle that must be ABSENT> <input> [override]
nobody() {
  local name="$1" needle="$2" input="$3" override="${4:-0}" out
  out="$(run_sut "$override" "$input")"
  if printf '%s' "$out" | grep -qF -- "$needle"; then
    fail=$((fail + 1)); printf '  FAIL  %s -- report wrongly contains %s\n' "$name" "$needle"
  else
    pass=$((pass + 1)); printf '  ok    %s\n' "$name"
  fi
}

echo "verdicts"
want "empty input"                clean     ""
want "no migration files"         clean     "$(rec modified backend/src/main/kotlin/com/bottlevault/App.kt)"
want "sql outside the mig dirs"   clean     "$(rec added backend/src/test/resources/fixture.sql)"
want "added SQL migration"        backup    "$(rec added "$SQLDIR/V10__x.sql")"
want "added Kotlin migration"     backup    "$(rec added "$KTDIR/V11__y.kt")"
want "added in a subdirectory"    backup    "$(rec added "$SQLDIR/sub/V12__z.sql")"
want "modified applied SQL"       violation "$(rec modified "$SQLDIR/V1__initial_schema.sql")"
want "status=changed on SQL"      violation "$(rec changed "$SQLDIR/V1__initial_schema.sql")"
want "modified Kotlin V8"         clean     "$(rec modified "$KTDIR/V8__backfill_normalized_names.kt")"
want "deleted SQL"                violation "$(rec removed "$SQLDIR/V2__seed_brands.sql")"
want "deleted Kotlin V8"          violation "$(rec removed "$KTDIR/V8__backfill_normalized_names.kt")"
want "renamed within the dir"     violation "$(rec renamed "$SQLDIR/V2__new.sql" "$SQLDIR/V2__seed_brands.sql")"
want "renamed OUT of the dir"     violation "$(rec renamed docs/old.sql "$SQLDIR/V2__seed_brands.sql")"
want "renamed INTO the dir"       backup    "$(rec renamed "$SQLDIR/V13__moved.sql" drafts/V13__moved.sql)"
want "copied into the dir"        backup    "$(rec copied "$SQLDIR/V14__c.sql" "$SQLDIR/V1__initial_schema.sql")"
want "unchanged is inert"         clean     "$(rec unchanged "$SQLDIR/V1__initial_schema.sql")"
want "worst case wins"            violation "$(rec added "$SQLDIR/V10__x.sql")
$(rec modified "$SQLDIR/V1__initial_schema.sql")"
want "realistic mixed PR"         backup    "$(rec modified backend/src/main/kotlin/com/bottlevault/BrandService.kt)
$(rec added "$SQLDIR/V10__x.sql")
$(rec modified frontend/src/App.tsx)"

echo "override label"
want "downgrades a violation"     backup    "$(rec modified "$SQLDIR/V1__initial_schema.sql")" 1
want "no effect on an add"        backup    "$(rec added "$SQLDIR/V10__x.sql")" 1
body "leaves a trace in the body" "Migration edit approved by label" \
                                            "$(rec modified "$SQLDIR/V1__initial_schema.sql")" 1

echo "broken input is loud, not clean"
want "unrecognized status"        EXIT2     "$(rec exploded "$SQLDIR/V1__initial_schema.sql")"
want "missing filename field"     EXIT2     "modified"

echo "report content"
body "carries the upsert marker"  "<!-- bottlevault-migration-gate -->" "$(rec added "$SQLDIR/V10__x.sql")"
body "names the fatal case"       "this is the fatal one"    "$(rec modified "$SQLDIR/V1__initial_schema.sql")"
body "offers the override label"  "migration-edit-approved"  "$(rec modified "$SQLDIR/V1__initial_schema.sql")"
body "points at the runbook"      "§5"                       "$(rec added "$SQLDIR/V10__x.sql")"
body "explains the Kotlin case"   "V8__backfill"             "$(rec modified "$KTDIR/V8__backfill_normalized_names.kt")"

# A clean verdict still emits a report when a Kotlin migration was edited, and
# that report must not borrow the backup-required copy. Getting this wrong made
# the gate tell a maintainer to snapshot the database for a change that needs no
# snapshot, while simultaneously removing the requires-backup label -- a gate
# that cries wolf is a gate people learn to ignore.
nobody "kt-only never claims an add"   "adds a database migration" \
                                       "$(rec modified "$KTDIR/V8__backfill_normalized_names.kt")"
nobody "kt-only never demands backup"  "**Before deploying:**" \
                                       "$(rec modified "$KTDIR/V8__backfill_normalized_names.kt")"
body   "kt-only says no backup needed" "no backup required" \
                                       "$(rec modified "$KTDIR/V8__backfill_normalized_names.kt")"

echo
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
