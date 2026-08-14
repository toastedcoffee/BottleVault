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
#   scripts/add-spdx-headers.sh --check   # report files missing headers, exit 1 if any
#   scripts/add-spdx-headers.sh           # apply headers in place

set -euo pipefail

ID_LINE='SPDX-License-Identifier: AGPL-3.0-only'
COPY_LINE='SPDX-FileCopyrightText: 2025-2026 toastedcoffee'

CHECK_ONLY=0
if [ "${1:-}" = "--check" ]; then
  CHECK_ONLY=1
fi

cd "$(git rev-parse --show-toplevel)"

# First-party source in scope.
#
# Deliberately absent, each for a reason:
#   backend/src/**/db/migration/*   V1-V6 are applied in production. Flyway
#                                   checksums whole file content, so a header
#                                   fails validation on startup. New migrations
#                                   (V7+) must be headered BEFORE first apply.
#   backend/gradlew*, gradle/wrapper Third-party; already carry Apache-2.0 SPDX.
#   frontend/tsconfig*.json         JSON has no comment syntax.
#   .env.example                    A template users copy; a header would
#                                   propagate into every self-hoster's .env.
#   .github/dependabot.yml          Not a workflow; outside the stated scope.
INCLUDE_RE='^(backend/src/.*\.kt|backend/src/.*/application.*\.yml|backend/(build|settings)\.gradle\.kts|backend/gradle\.properties|frontend/src/.*\.(ts|tsx|css)|frontend/index\.html|frontend/(vite\.config\.ts|eslint\.config\.js|postcss\.config\.js|nginx\.conf)|edge/.*\.(js|html|toml)|scripts/.*\.sh|(backend|frontend)/Dockerfile|docker-compose.*\.yml|\.github/workflows/.*\.yml)$'

comment_style() {
  case "$1" in
    *.css)                echo block ;;
    *.html)               echo html  ;;
    *.kt|*.kts|*.ts|*.tsx|*.js) echo slash ;;
    *.sql)                echo sql   ;;
    *)                    echo hash  ;;
  esac
}

header_for() {
  case "$1" in
    slash) printf '// %s\n// %s\n'         "$ID_LINE" "$COPY_LINE" ;;
    hash)  printf '# %s\n# %s\n'           "$ID_LINE" "$COPY_LINE" ;;
    block) printf '/* %s */\n/* %s */\n'   "$ID_LINE" "$COPY_LINE" ;;
    html)  printf '<!-- %s -->\n<!-- %s -->\n' "$ID_LINE" "$COPY_LINE" ;;
    sql)   printf -- '-- %s\n-- %s\n'      "$ID_LINE" "$COPY_LINE" ;;
  esac
}

apply_header() {
  local f="$1" style hdr tmp
  style="$(comment_style "$f")"
  # Command substitution strips trailing newlines, so `hdr` loses the newline
  # after the final header line. It is restored by the `\n` in the printf
  # below; without it the file's original first line fuses onto the header.
  hdr="$(header_for "$style")"
  tmp="$(mktemp)"
  if head -1 "$f" | grep -q '^#!'; then
    # Shebang must stay on line 1 or the script stops being executable.
    { head -1 "$f"; printf '%s\n' "$hdr"; tail -n +2 "$f"; } > "$tmp"
  else
    { printf '%s\n' "$hdr"; cat "$f"; } > "$tmp"
  fi
  # Write through the original inode rather than mv, to preserve the file mode
  # (notably the executable bit on scripts/*.sh).
  cat "$tmp" > "$f"
  rm -f "$tmp"
}

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
done < <(git ls-files | grep -E "$INCLUDE_RE")

echo "---"
echo "files in scope:        $(git ls-files | grep -cE "$INCLUDE_RE")"
echo "files needing headers: $changed"

if [ "$CHECK_ONLY" -eq 1 ] && [ "$changed" -gt 0 ]; then
  exit 1
fi
exit 0
