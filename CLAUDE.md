# Notes for Claude

## Privacy

This is a **public repo**. The maintainer's real first name must not appear
in any artifact that ends up on GitHub — commit messages, commit authors,
PR titles/bodies, issues, code, comments, README, example values, or docs.

When a human referent is needed, use neutral terms: "the user", "the
maintainer", "the author", or just "you". For example emails in docs or
test plans, use `user@example.com` — never a name-shaped local-part.

The GitHub identity for this repo is `toastedcoffee`. Only refer to the
maintainer by that handle or the neutral terms above.

"DOM" (Document Object Model) and "domain" are fine — those aren't the
maintainer's name.

## Testing before shipping

When you write code — especially shell scripts, SQL, or anything that runs
against a live environment — run it end-to-end before opening a PR. If the
environment isn't accessible from your sandbox (e.g. the script targets a
server the maintainer runs), say so explicitly: "this is untested, here's
what to verify before merging." Never imply something works when you only
reasoned about it.

A test plan in a PR body is a promise to the reviewer. Either you executed
the checks, or you flag clearly that they're still outstanding.

## Review gates

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

## Deployment model (TrueNAS / Dockge)

Prod runs from `docker-compose.prod.yml` pasted into Dockge — there is **no git
clone on the server**. Images come from GHCR
(`ghcr.io/toastedcoffee/bottlevault-{api,web}`). To test a feature branch on
TrueNAS: trigger the CI `workflow_dispatch` with a `tag` input, then point the
Dockge stack at `:<tag>` instead of `:latest`. CI only overwrites `:latest`
from `main`.
