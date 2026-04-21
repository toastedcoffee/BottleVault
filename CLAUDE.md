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
