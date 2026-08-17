# Notes for Claude

## Project stack

React + TypeScript (Vite) frontend, Kotlin + Spring Boot backend, PostgreSQL
(H2 in the `dev` profile), Docker Compose deployment. This is a web app — there
is no Android/Kotlin-mobile app in this repo. If a spec, ticket, or your own
memory assumes a different platform, verify against the actual current repo
structure (`frontend/`, `backend/`) before implementing anything.

## Privacy

This is a **public repo**. The maintainer's real first name must not appear
in any artifact that ends up on GitHub — commit messages, commit authors,
PR titles/bodies, issues, code, comments, README, example values, or docs.

When a human referent is needed, use neutral terms: "the user", "the
maintainer", "the author", or just "you". For example emails in docs or
test plans, use `user@example.com` — never a name-shaped local-part.

The GitHub identity for this repo is `toastedcoffee`. Only refer to the
maintainer by that handle or the neutral terms above.

## Testing before shipping

When you write code — especially shell scripts, SQL, or anything that runs
against a live environment — run it end-to-end before opening a PR. If the
environment isn't accessible from your sandbox (e.g. the script targets a
server the maintainer runs), say so explicitly: "this is untested, here's
what to verify before merging." Never imply something works when you only
reasoned about it.

A test plan in a PR body is a promise to the reviewer. Either you executed
the checks, or you flag clearly that they're still outstanding.

For large multi-file efforts (restyles, migrations), plan phases and land or
verify them incrementally rather than emitting one giant change — long single
outputs have been lost to output-limit truncation before.

## Database portability: H2 vs Postgres

Flyway migrations and any SQL shared across profiles must parse under **both**
real PostgreSQL and H2 in PostgreSQL mode (the `dev` profile). No plpgsql
`DO` blocks or Postgres-only DDL in shared paths — see the comments in
`V6__refresh_token_hash_varchar.sql` for the established pattern. Behavior
that genuinely needs real Postgres belongs in an integration test extending
`AbstractPostgresIntegrationTest` (Testcontainers, postgres:16-alpine).

The Windows Docker daemon path and the docker-java `api.version` pin are
handled in `backend/build.gradle.kts` (tasks.withType<Test>) with comments
explaining why — don't remove those blocks.

## Review gates

The global `/ship` skill drives this entire flow — tests, reviews, gate
markers, PR, CI wait, merge, cleanup — in one invocation. Use it for routine
landings; the manual steps below remain the definition of what must happen
and the fallback if the skill isn't available.

Run `/code-review` before every **push/PR** — not every commit (commits are
local checkpoints; the push is when code leaves the machine). Re-review after
any fix that **changes logic**; pure style/comment tweaks don't reset the gate.
A PR is merge-ready only after a final `/code-review` **and** `/security-review`
both pass on the exact commit being merged. Enforcement lives in settings.json
hooks; this is the intent behind them.

Two markers satisfy the gates (both gitignored, under `.claude/`):
- **PR-create gate** — after reviews pass, write `.claude/.pr-reviews-done`;
  consumed automatically after `gh pr create`.
- **Merge gate** — after the final reviews pass on the branch tip, run
  `git rev-parse HEAD > .claude/.pr-merge-ready`. The hook blocks `gh pr merge`
  unless that SHA still matches HEAD, so any new commit forces a re-review.
  (Leak scanning + the merge gate are global, in `~/.claude/hooks`.)

## Backend: JPA lazy loading

`Bottle.product` and `Bottle.user` are `@ManyToOne(LAZY)`. Call mapper
functions like `BottleResponse.from(bottle)` *inside* the `@Transactional`
service method — not from the controller. With `open-in-view: false` (set in
application.yml), touching a lazy association after the transaction closes
throws `LazyInitializationException`.

## Frontend: authenticated binary endpoints

Protected endpoints returning binary content (e.g. `GET /api/bottles/{id}/image`)
can't be loaded by a plain `<img src>` — the browser won't attach the JWT from
sessionStorage. Pattern: fetch the bytes through the axios client (interceptor
adds the header), render via `URL.createObjectURL`, revoke on unmount. See
`frontend/src/components/bottles/BottleImage.tsx`.

## Upload size limits

Two caps must stay aligned, or nginx 413s a request the backend would accept:
- `application.yml` → `spring.servlet.multipart.max-file-size` (backend cap)
- `frontend/nginx.conf` → `client_max_body_size` on `/api/` (proxy cap, keep
  ~1 MB above backend cap for multipart overhead)

## Windows / shell conventions

Prefer the Bash tool (Git Bash) for git/npm/gradle commands — it takes normal
POSIX quoting. Reach for the PowerShell tool only when a command needs it
specifically (native Windows services, registry, etc.), and avoid nested or
nakedly-escaped quotes there (e.g. `ssh-keygen -N '""'`, `sc create ... binPath=
"..."`) — PowerShell's quote mangling has silently produced wrong values (like
a passphrase-protected key) more than once. When in doubt, write the value to
a temp file or use a here-string instead of inline escaping.

## Deployment model (TrueNAS / Dockge)

Prod runs from `docker-compose.prod.yml` pasted into Dockge — there is **no git
clone on the server**. Images come from GHCR
(`ghcr.io/toastedcoffee/bottlevault-{api,web}`). To test a feature branch on
TrueNAS: trigger the CI `workflow_dispatch` with a `tag` input, then point the
Dockge stack at `:<tag>` instead of `:latest`. CI only overwrites `:latest`
from `main`.

For updating a running instance safely — which changes need a backup, which
cause downtime, and the backup/restore commands — see [DEPLOY.md](DEPLOY.md).
A change is backup-required if and only if it adds a file under
`backend/src/main/resources/db/migration/`.

## Licensing conventions

This repo is **AGPL-3.0-only** — full text in `LICENSE`, notice in `COPYRIGHT`.
Trademark terms live in `TRADEMARKS.md` and are **not** covered by the AGPL.
**Never reintroduce "MIT" anywhere in the repo.**

The Flyway rule comes first, because getting it wrong takes prod down:
**never add an SPDX header to a migration that has already been applied to a
live database.** Flyway checksums whole file content, so a header changes the
checksum, fails validation on the next startup, and takes the API down. `V1`-`V6`
are applied. **Never run `flyway repair`.** New migrations get their header
*before* they are first applied — and migrations stay out of the sweep script by
an explicit `EXCLUDE_RE` gate, not merely by omission from `INCLUDE_RE`.

Every new first-party source file gets an SPDX header, two lines:
`SPDX-License-Identifier: AGPL-3.0-only` and
`SPDX-FileCopyrightText: 2025-2026 toastedcoffee`.
- `bash scripts/add-spdx-headers.sh --check` lists files missing one; without
  `--check` it adds them. It is idempotent.
- The scripts are mode `100644` (not executable) on this Windows-managed repo,
  so **invoke as `bash scripts/add-spdx-headers.sh`**, not
  `./scripts/add-spdx-headers.sh`.
- Exit codes: `0` = clean (nothing missing), `1` = files missing headers,
  `2` = **broken tool** (e.g. scope collapsed to near-zero — a floor that
  catches the tool silently finding nothing rather than reporting a false clean).
- `EXCLUDE_RE` (migrations, `gradlew`, wrapper files) is a **hard gate that
  `INCLUDE_RE` cannot override**. That is what makes the Flyway mistake above
  structurally hard to reintroduce, even if someone later edits `INCLUDE_RE`
  carelessly.
- **CSS uses `/* ... */`** for the header, never `//` — `//` is not valid CSS
  and breaks the build.
- Latent bug worth remembering if scope ever changes: the idempotency check does
  `head -5 "$f" | grep -q ...`. If a file ever holds more than ~64KB within its
  first 5 lines, `grep -q` can exit on match while `head` is still writing,
  `head` dies with SIGPIPE (exit 141), and `pipefail` propagates that as a false
  condition — re-applying a header to an already-headered file. Unreachable
  today (the largest real first-5-lines payload here is 345 bytes, three orders
  of magnitude below the threshold); it goes live the moment minified CSS or a
  bundled `edge/*.js` file enters `INCLUDE_RE` scope.

Dockerfiles have two licensing-adjacent traps:
- `# syntax=` and `# escape=` parser directives must precede **all** content,
  comments included. The SPDX header now occupies lines 1-2 of both Dockerfiles,
  so a directive added below it is silently ignored — if one is ever needed, it
  goes *above* the SPDX header.
- The frontend build injects the commit SHA via the Docker build arg `GIT_SHA`
  into `VITE_GIT_SHA`. Keep `ARG GIT_SHA` **inside the build stage that consumes
  it** (the `FROM ... AS build` stage, above the build `RUN`). An `ARG` declared
  outside that stage is invisible to it, and the frontend silently ships the
  `dev` sentinel instead of the real commit SHA — with no error.

The AGPL section 13 source link in the app UI is a **compliance requirement, not
decoration**. `SourceOffer.tsx` renders on the login page and in the app shell
footer; it **must stay reachable while logged out**. Do not remove it.

Trademark usage:
- Use `™` (U+2122) at first prominent use on branding surfaces only — never `®`,
  and never in variable names, package names, database values, or API responses.
- **A shared component carries no mark; each surface marks its own most
  prominent wordmark once.** The app shell nav and the login page count as
  separate surfaces (a logged-out user never sees the nav), so the nav wordmark
  and the login `<h1>` wordmark each carry `™`, while `SourceOffer.tsx` — mounted
  on both — carries none. Follow the same pattern for any future shared component
  that touches branding text. This supersedes the original relicensing plan.
- `TRADEMARKS.md:7` reads "the AGPL-3.0 license" in prose, where everywhere else
  the SPDX identifier is always `AGPL-3.0-only`. That line is a **deliberate,
  maintainer-approved prose exception — do not "fix" it.**

New dependencies must themselves be MIT, BSD, ISC, Apache-2.0, or another
AGPL-compatible license. **Flag GPL-2.0-only, LGPL, MPL, EPL, SSPL, or BUSL
before adding.**
