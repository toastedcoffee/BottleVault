#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# Classifies a pull request's file changes with respect to Flyway migrations,
# and reports whether the PR needs a pre-deploy backup or is doing something
# Flyway will refuse at startup.
#
# The two cases are deliberately not the same severity:
#
#   Adding a migration risks forgetting a backup. The deploy still succeeds;
#   you just have no rollback if the migration turns out to be wrong. Advisory.
#
#   Changing a migration that already exists on the base branch means the
#   deploy fails. Flyway validates on startup, finds a checksum mismatch (or a
#   missing applied migration), and refuses to boot. The API is down until
#   someone reverts the file, and `flyway repair` is forbidden here. Blocking.
#
# Usage:
#   gh api "repos/$REPO/pulls/$N/files" --paginate \
#     --jq '.[] | [.status, .filename, (.previous_filename // "")] | @tsv' \
#     | scripts/check-migration-changes.sh
#
# Input, on stdin: one tab-separated record per changed file --
#
#     <status>\t<filename>\t<previous_filename>
#
# The third field is empty except on renames and copies. Statuses are
# GitHub's: added, removed, modified, renamed, copied, changed, unchanged.
#
# Reading TSV rather than JSON is deliberate. `gh` embeds jq for `--jq`, so the
# API call can shape the data at the source; making this script parse JSON too
# would mean depending on a *separate* jq binary that the runner has but a
# Windows dev machine does not, which would make the gate untestable in exactly
# the place it gets edited.
#
# `status` is computed against the merge base, so a file added and then edited
# within the same PR still reads as `added`. That is what makes "was this file
# already on the base branch?" answerable without a version floor to maintain --
# and a floor would already be stale, since CLAUDE.md's "V1-V6 are applied"
# predates V7-V9.
#
# Output: line 1 is `VERDICT=clean|backup|violation`. Lines 2+ are a Markdown
# report suitable for posting as a PR comment (empty when there is nothing at
# all to say).
#
# Environment:
#   RUNBOOK_BASE     URL (or path) used to link DEPLOY.md in the report.
#   OVERRIDE_LABEL   Name of the label that downgrades a violation.
#   OVERRIDE_ACTIVE  "1" when that label is present on the PR. A violation is
#                    then reported as VERDICT=backup with the override recorded
#                    in the body, so the escape hatch leaves a trace.
#
# Exit codes:
#   0  classification succeeded -- read VERDICT, never infer from the exit code
#   2  the tool is broken (malformed input record). Never treat this as a clean
#      result; it is the same "loud rather than silently finding nothing"
#      contract as scripts/add-spdx-headers.sh.

set -euo pipefail

RUNBOOK_BASE="${RUNBOOK_BASE:-DEPLOY.md}"
OVERRIDE_LABEL="${OVERRIDE_LABEL:-migration-edit-approved}"
OVERRIDE_ACTIVE="${OVERRIDE_ACTIVE:-0}"

MARKER='<!-- bottlevault-migration-gate -->'

# Both migration roots. Missing either one produces a gate that looks like it
# works: the SQL migrations live under resources/, but V8 is a Kotlin
# BaseJavaMigration under kotlin/, and a filter written against resources/
# alone ignores it silently.
#
# The trailing patterns are `.+` rather than `[^/]+` because Flyway scans its
# migration locations recursively, so a file in a subdirectory is still a
# migration. Matching more than strictly necessary is the safe direction here --
# the same "strictly coarser, over-report rather than under-report" property
# DEPLOY.md 7a establishes for its pre-check queries.
SQL_RE='^backend/src/main/resources/db/migration/.+\.sql$'
KT_RE='^backend/src/main/kotlin/db/migration/.+\.kt$'

added=""
removed=""
renamed=""
sql_modified=""
kt_modified=""
n_added=0
n_immutable=0
n_kt_modified=0

# Each list is built newline-terminated rather than newline-separated. That
# avoids a nameref helper, and the trailing blank is harmless because the
# renderer skips empty lines.
NL=$'\n'

lineno=0
while IFS=$'\t' read -r status filename previous || [ -n "${status:-}" ]; do
  lineno=$((lineno + 1))
  # Skip blank lines: a trailing newline on the input is normal, not an error.
  [ -z "${status}${filename:-}" ] && continue

  if [ -z "${filename:-}" ]; then
    echo "ERROR: malformed input at record $lineno: expected <status>TAB<filename>[TAB<previous>]" >&2
    exit 2
  fi

  # For everything except a rename or copy, the old path is the current one.
  old="${previous:-}"
  [ -z "$old" ] && old="$filename"

  new_is_mig=0
  old_is_mig=0
  old_is_sql=0
  old_is_kt=0
  [[ "$filename" =~ $SQL_RE || "$filename" =~ $KT_RE ]] && new_is_mig=1
  [[ "$old" =~ $SQL_RE ]] && { old_is_mig=1; old_is_sql=1; }
  [[ "$old" =~ $KT_RE  ]] && { old_is_mig=1; old_is_kt=1; }

  case "$status" in
    added|copied)
      if [ "$new_is_mig" -eq 1 ]; then
        added="$added$filename$NL"; n_added=$((n_added + 1))
      fi
      ;;
    removed)
      # Flyway validate fails on an applied migration that is missing locally,
      # so a delete is exactly as fatal as an edit.
      if [ "$old_is_mig" -eq 1 ]; then
        removed="$removed$old$NL"; n_immutable=$((n_immutable + 1))
      fi
      ;;
    renamed)
      # A rename is a delete plus an add. Judged on the OLD path, so a
      # migration renamed *out* of the directory is still caught even though
      # its new path no longer matches.
      if [ "$old_is_mig" -eq 1 ]; then
        renamed="$renamed$old  ->  $filename$NL"; n_immutable=$((n_immutable + 1))
      elif [ "$new_is_mig" -eq 1 ]; then
        added="$added$filename$NL"; n_added=$((n_added + 1))
      fi
      ;;
    modified|changed)
      # The asymmetry that matters, and it is counterintuitive:
      # BaseJavaMigration.getChecksum() returns null unless overridden, so
      # Flyway does not checksum a Kotlin migration's content. Editing V8 is
      # safe where editing an applied .sql file is not -- which is also why V8
      # can carry an SPDX header and the applied .sql migrations cannot.
      if [ "$old_is_sql" -eq 1 ]; then
        sql_modified="$sql_modified$filename$NL"; n_immutable=$((n_immutable + 1))
      elif [ "$old_is_kt" -eq 1 ]; then
        kt_modified="$kt_modified$filename$NL"; n_kt_modified=$((n_kt_modified + 1))
      fi
      ;;
    unchanged)
      ;;
    *)
      echo "ERROR: unrecognized file status '$status' at record $lineno." >&2
      exit 2
      ;;
  esac
done

if [ "$n_immutable" -gt 0 ]; then
  verdict="violation"
elif [ "$n_added" -gt 0 ]; then
  verdict="backup"
else
  verdict="clean"
fi

overridden=0
if [ "$verdict" = "violation" ] && [ "$OVERRIDE_ACTIVE" = "1" ]; then
  overridden=1
  # Still backup-required: whatever the PR does to an existing migration, the
  # database it runs against is about to change shape.
  verdict="backup"
fi

echo "VERDICT=$verdict"

if [ "$verdict" = "clean" ] && [ "$n_kt_modified" -eq 0 ]; then
  exit 0
fi

bullets() {
  # The `if` is deliberate: with `[ -n "$line" ] && printf ...` as the loop's
  # last command, a trailing blank line makes the loop -- and therefore this
  # function -- return non-zero, which under `set -e` kills the script at the
  # call site. The lists are newline-terminated, so that blank line is the
  # normal case, not an edge one.
  while IFS= read -r line; do
    if [ -n "$line" ]; then printf -- '- `%s`\n' "$line"; fi
  done <<< "$1"
  return 0
}

printf '%s\n\n' "$MARKER"

if [ "$overridden" -eq 1 ]; then
  cat <<EOF
### ⚠️ Migration edit approved by label — check downgraded

This PR changes a migration that already exists on the base branch, which
normally fails this check. The \`$OVERRIDE_LABEL\` label is applied, so it has
been downgraded to backup-required.

That override is only correct if the migration is on the base branch but **has
not been deployed yet** — CI publishes \`:latest\` on merge, but deploys are
manual, so there is a real window where editing is safe. If it has already been
deployed, remove the label and add a new migration instead: Flyway will refuse
to boot on the checksum mismatch, and \`flyway repair\` is forbidden in this
repo.

EOF
elif [ "$verdict" = "violation" ]; then
  cat <<EOF
### 🛑 This PR changes a migration that already exists on the base branch

Flyway checksums applied migrations and validates on startup. A migration that
is edited, deleted, or renamed after it has run against the live database fails
validation, and **the API will not boot** until the file is put back.
\`flyway repair\` is forbidden in this repo, so there is no quick fix at deploy
time.

EOF
else
  cat <<EOF
### ⚠️ This PR adds a database migration — back up before deploying

A new file under a migration directory is the single signal that a backup is
required. Merging is fine; deploying without a snapshot is not.

EOF
fi

if [ -n "$sql_modified" ]; then
  printf '**Modified (checksummed — this is the fatal one):**\n'
  bullets "$sql_modified"
  printf '\n'
fi
if [ -n "$removed" ]; then
  printf '**Deleted (validate fails on a missing applied migration):**\n'
  bullets "$removed"
  printf '\n'
fi
if [ -n "$renamed" ]; then
  printf '**Renamed (a delete plus an add, so both failures apply):**\n'
  bullets "$renamed"
  printf '\n'
fi
if [ -n "$added" ]; then
  printf '**Added:**\n'
  bullets "$added"
  printf '\n'
fi
if [ -n "$kt_modified" ]; then
  printf '**Modified Kotlin migration (not a checksum problem):**\n'
  bullets "$kt_modified"
  cat <<'EOF'

`BaseJavaMigration.getChecksum()` returns null unless overridden, so Flyway does
not checksum this file's content and validation will not fail. Worth a second
look anyway: it has already run in production and will never re-run there, so an
edit only changes what happens on a **fresh** database.

EOF
fi

if [ "$verdict" = "violation" ]; then
  cat <<EOF
**What to do instead:** leave the existing file untouched and add a new
\`V<n>__…\` migration that makes the change forward-only.

**If it is genuinely safe** — the migration is on the base branch but has not
been deployed yet — apply the \`$OVERRIDE_LABEL\` label to record that
judgement. The check re-runs on label changes and will go green, and the
override stays visible in this comment.
EOF
else
  cat <<EOF
**Before deploying:** back up first ([DEPLOY.md]($RUNBOOK_BASE) §4 — the ZFS
snapshot is the primary method; it captures the database *and* uploads
atomically, which \`pg_dump\` alone does not), then follow §5 for the
migration-bearing update.

**Also classify it** (§1): \`CREATE TABLE\` / \`ADD COLUMN\` / \`CREATE INDEX\`
is additive and Class A, no maintenance window. A \`DROP\`, \`RENAME\`,
\`ALTER … TYPE\`, \`SET NOT NULL\`, or any \`UPDATE\`/\`DELETE\` backfill is
Class B: 2–5 minutes of real downtime, and it needs testing against a copy of
prod data first. Make that call now, while the PR is open — a rename that slips
through as Class A is exactly the case that breaks.
EOF
fi
