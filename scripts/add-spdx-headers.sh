#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# Adds SPDX licence headers to first-party source files. Idempotent: any file
# already containing SPDX-License-Identifier is skipped, so this is safe to
# re-run after adding new source files.
#
# Enumeration is driven by `git ls-files`, not `find`. That is load-bearing:
# it respects .gitignore (so node_modules/, build/, dist/, .gradle/, .idea/ are
# structurally excluded), it never descends into the registered git worktrees
# under .claude/worktrees/, and it never touches an untracked file.
#
# Usage:
#   scripts/add-spdx-headers.sh --check   # report files missing headers
#   scripts/add-spdx-headers.sh           # apply headers in place
#
# Exit codes:
#   0  every file in scope carries a header
#   1  --check only: one or more files are missing a header
#   2  the tool itself is broken (not in a git repo, scope implausibly small,
#      or an unmapped comment style) -- never treat this as a clean result

set -euo pipefail

ID_LINE='SPDX-License-Identifier: AGPL-3.0-only'
COPY_LINE='SPDX-FileCopyrightText: 2025-2026 toastedcoffee'

CHECK_ONLY=0
if [ "${1:-}" = "--check" ]; then
  CHECK_ONLY=1
fi

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$repo_root" ]; then
  echo "ERROR: not inside a git repository." >&2
  exit 2
fi
cd "$repo_root"

# First-party source in scope.
#
# Deliberately absent, each for a reason:
#   backend/gradlew*, gradle/wrapper Third-party; already carry Apache-2.0 SPDX.
#   frontend/tsconfig*.json         JSON has no comment syntax.
#   .env.example                    A template users copy; a header would
#                                   propagate into every self-hoster's .env.
#   .github/dependabot.yml          Not a workflow; outside the stated scope.
#   frontend/public/favicon.svg     Artwork served verbatim to every visitor.
#                                   An XML comment would add bytes to each page
#                                   load for no compliance benefit.
#   db/migration/*.sql              See EXCLUDE_RE below -- this one is a safety
#                                   gate, not a scope decision.
INCLUDE_RE='^(backend/src/.*\.kt|backend/src/.*/application.*\.yml|backend/(build|settings)\.gradle\.kts|backend/gradle\.properties|frontend/src/.*\.(ts|tsx|css)|frontend/index\.html|frontend/(vite\.config\.ts|eslint\.config\.js|postcss\.config\.js|nginx\.conf)|edge/.*\.(js|html|toml)|scripts/.*\.sh|(backend|frontend)/Dockerfile|docker-compose.*\.yml|\.github/workflows/.*\.yml)$'

# Hard exclusions, applied AFTER INCLUDE_RE and NOT overridable by it. A path
# matching EXCLUDE_RE is skipped even if INCLUDE_RE matches.
#
# This is a safety gate, not a scope preference. Flyway checksums whole file
# content, so adding a header to an already-applied migration changes its
# checksum and fails validation on next startup -- taking the production API
# down. Protecting those files by merely leaving `.sql` out of INCLUDE_RE is too
# weak a guarantee for a failure that severe: one plausible-looking edit to a
# regex would be enough.
#
# This script therefore NEVER touches a migration. A new migration's SPDX header
# is written by hand when the migration is authored, before it is first applied:
#   -- SPDX-License-Identifier: AGPL-3.0-only
#   -- SPDX-FileCopyrightText: 2025-2026 toastedcoffee
# Never run `flyway repair` to work around a checksum mismatch caused by a header.
EXCLUDE_RE='(^backend/src/.*/db/migration/|^backend/gradlew|^backend/gradle/wrapper/)'

comment_style() {
  case "$1" in
    *.css)                echo block ;;
    *.html)               echo html  ;;
    *.kt|*.kts|*.ts|*.tsx|*.js) echo slash ;;
    *)                    echo hash  ;;
  esac
}

header_for() {
  case "$1" in
    slash) printf '// %s\n// %s\n'         "$ID_LINE" "$COPY_LINE" ;;
    hash)  printf '# %s\n# %s\n'           "$ID_LINE" "$COPY_LINE" ;;
    block) printf '/* %s */\n/* %s */\n'   "$ID_LINE" "$COPY_LINE" ;;
    html)  printf '<!-- %s -->\n<!-- %s -->\n' "$ID_LINE" "$COPY_LINE" ;;
    *)     echo "ERROR: unknown comment style '$1'" >&2; exit 2 ;;
  esac
}

apply_header() {
  local f="$1" style tmp
  style="$(comment_style "$f")"
  tmp="$(mktemp)"
  trap 'rm -f "$tmp"' RETURN
  # header_for is called directly, never through command substitution: `$(...)`
  # strips trailing newlines, which previously fused each file's original first
  # line onto the copyright line. Do not "simplify" this back into a variable.
  if head -1 "$f" | grep -q '^#!'; then
    # Shebang must stay on line 1 or the script stops being executable.
    { head -1 "$f"; header_for "$style"; tail -n +2 "$f"; } > "$tmp"
  else
    { header_for "$style"; cat "$f"; } > "$tmp"
  fi
  # Write through the original inode rather than mv, to preserve the file mode
  # (notably the executable bit on scripts/*.sh).
  cat "$tmp" > "$f"
}

# A scope far below the real file count means the regex is broken or the script
# is running somewhere unexpected -- NOT that everything is already headered.
# Without this floor both conditions exit 0 and look identical, which would make
# `--check` useless as a CI gate.
MIN_SCOPE=100

scope_count="$(git ls-files | grep -E "$INCLUDE_RE" | grep -vE "$EXCLUDE_RE" | wc -l | tr -d ' ')"

if [ "$scope_count" -lt "$MIN_SCOPE" ]; then
  echo "ERROR: only $scope_count files matched (expected at least $MIN_SCOPE)." >&2
  echo "The tool is broken or running outside the repo. This is NOT a clean result." >&2
  exit 2
fi

changed=0
while IFS= read -r f; do
  [ -f "$f" ] || continue
  if grep -q 'SPDX-License-Identifier' "$f"; then
    continue
  fi
  changed=$((changed + 1))
  if [ "$CHECK_ONLY" -eq 1 ]; then
    echo "missing header: $f"
  else
    apply_header "$f"
    echo "headered: $f"
  fi
done < <(git ls-files | grep -E "$INCLUDE_RE" | grep -vE "$EXCLUDE_RE")

echo "---"
echo "files in scope:        $scope_count"
echo "files needing headers: $changed"

if [ "$CHECK_ONLY" -eq 1 ] && [ "$changed" -gt 0 ]; then
  exit 1
fi
exit 0
