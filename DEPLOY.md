# Deploying and updating BottleVault

How to update the running app **without losing user data** and while knowing,
before you pull, whether a change is a routine refresh or one that needs a
backup and a maintenance window.

Prod runs from [`docker-compose.prod.yml`](docker-compose.prod.yml) pasted into
Dockge on TrueNAS — there is no git clone on the server. Images come from GHCR
(`ghcr.io/toastedcoffee/bottlevault-{api,web}`). CI overwrites `:latest` only
from `main`; feature branches publish to `:<tag>` via a `workflow_dispatch`
input. See CLAUDE.md's "Deployment model" section for the source of truth on
that flow.

---

## 1. Classify the change first

Every update falls into one of these. Decide which **before** touching prod.

| Change type | Category | What to do | User impact |
|---|---|---|---|
| Frontend only (web image) | ✅ Safe | Pull + `up -d` | ~seconds while `web` recreates |
| Backend code, **no** new migration | ✅ Safe | Pull + `up -d` | ~seconds + up to ~40s until the `api` healthcheck passes |
| Compose / env-var change only | ✅ Safe | `up -d` (no pull needed) | Restarts the affected service(s) |
| **Additive** migration (new `V*.sql`: new table/column, new index) | ⚠️ Back up first | Snapshot → test on `:<tag>` → deploy | `api` restart while Flyway applies |
| **Destructive / backfill** migration (drop or rename a column, move data) | 🛑 Back up + planned window | Snapshot → test on a **copy** of prod data → deploy in a quiet window | Potentially longer; data at risk if the migration is wrong |
| Postgres **major** version bump (e.g. 16 → 17) | 🛑 Back up + planned | `pg_dump`/restore or `pg_upgrade`; never just repoint the image | Real downtime |

**Honest caveat:** the stack is single-replica with no rolling deploy, so *every*
update is a brief blip today. True zero-downtime (two `web` replicas or
blue/green behind the tunnel) is a separate infrastructure change, not covered
here.

### How to tell which row you're in

From a checkout of the commit range you're about to deploy:

```bash
# Does this update include a database migration? If this prints anything,
# you are in a ⚠️ / 🛑 row — back up before deploying.
#
# Both paths matter. The SQL migrations live under resources/, but a Flyway
# JVM migration (V8) is Kotlin under kotlin/db/migration/, and checking only
# the first path misses it silently.
git diff --name-only <deployed-sha>..<new-sha> --   backend/src/main/resources/db/migration/   backend/src/main/kotlin/db/migration/

# Which parts changed at all?
git diff --stat <deployed-sha>..<new-sha>
```

A migration file under **either** of those directories is the single signal
that a backup is required. Whether it's "additive" or "destructive" is a
judgement call — read the SQL: `CREATE TABLE`/`ADD COLUMN`/`CREATE INDEX` is
additive; `DROP`, `ALTER ... TYPE`, `RENAME`, or any `UPDATE`/`DELETE` backfill
is destructive and needs the full-care path.

### The CI gate answers this before the deploy

[`.github/workflows/migration-gate.yml`](.github/workflows/migration-gate.yml)
runs the same classification on every PR, so the question gets asked while the
PR is open rather than while the deploy is running. It separates two cases that
are **not** the same severity:

| What the PR does | Gate response | Why |
|---|---|---|
| **Adds** a migration | Labels it `requires-backup`, comments §4/§5, check stays **green** | Forgetting a backup costs you the rollback, not the deploy. Advisory. |
| **Edits, deletes, or renames** a migration already on `main` | Check goes **red** | Flyway checksums applied migrations and validates at startup. The API will not boot, and `flyway repair` is forbidden here. A label cannot stop a merge; this needs to. |

The rule for "already on `main`" is the PR's own diff status against the merge
base, not a hardcoded version floor. A floor rots — this file and CLAUDE.md both
said "V1-V6 are applied" while V7-V9 were live.

One asymmetry is deliberate and counterintuitive. `BaseJavaMigration.getChecksum()`
returns null unless overridden, so Flyway does **not** checksum a Kotlin
migration's content: editing V8 is safe where editing an applied `.sql` file is
not, which is also why V8 can carry an SPDX header and the applied `.sql`
migrations cannot. Deleting or renaming V8 still breaks validation, so that
case still blocks.

There is a real window where editing a migration is safe — it is on `main` but
not yet deployed, since CI publishes `:latest` on merge while deploys are
manual. Rather than try to detect that, the gate blocks by default and takes an
explicit override: apply the `migration-edit-approved` label and the check
re-runs and goes green, with the override recorded in its PR comment.

The classifier is [`scripts/check-migration-changes.sh`](scripts/check-migration-changes.sh),
which reads a PR's file list on stdin, so you can run it by hand against any PR
without waiting for CI:

```bash
gh api "repos/toastedcoffee/BottleVault/pulls/<n>/files" --paginate   --jq '.[] | [.status, .filename, (.previous_filename // "")] | @tsv'   | bash scripts/check-migration-changes.sh
```

### Does this release need a maintenance window?

The table above answers *"do I need a backup?"* — that axis is about data safety
and is unchanged. This one answers *"will users notice?"*, and it exists because
the answer stopped being "always" once the stack learned to shut down cleanly and
serve the SPA independently of the API.

| Class | What it covers | User impact | Procedure |
|---|---|---|---|
| **A — no window** | Frontend-only changes. Backend changes with no migration. **Additive** migrations: new tables, new nullable columns, new indexes built `CONCURRENTLY`. | SPA keeps serving throughout. `/api/` returns 502 for ~30–60s while the API restarts; the in-app banner explains it. | Pull + `up -d`. |
| **B — window** | **Destructive or incompatible** migrations: drops, renames, `NOT NULL` on an existing column, type changes. Postgres version bumps. TrueNAS updates. Anything where old code would break against the new schema. | Real downtime, 2–5 minutes. | Snapshot → flip the maintenance flag → pull + `up -d` → verify → unflip. |

Class B is not a failure. For a closed beta, a branded "back in 15 minutes" page
and a note to users is a completely adequate answer, and it is the answer that
doesn't add a distributed system to the maintenance burden.

**The distinction is only meaningful if you make it during review.** A rename that
slips through as Class A is exactly the case that breaks. Ask the question when
the PR is opened, not when the deploy is running.

### Snapshot before every migration

Once PGDATA lives on its own ZFS dataset, a pre-migration snapshot is one command
and gives you instant rollback:

```bash
./scripts/pg-snapshot.sh <pool>/appdata/bottlevault-pg
```

Run it on the TrueNAS host as root, before `docker compose pull`. The script
prints the rollback procedure on completion. It refuses to run against a dataset
that has children, since that is the signature of a non-dedicated dataset whose
rollback would take unrelated data with it.

This is a **local** safety net for the likeliest data-loss event — a bad migration
or an application bug — not disaster recovery. It does not survive pool loss.
Offsite, point-in-time backup (e.g. pgBackRest to object storage) is a separate
layer and is not yet set up for this deployment.

### Pin your images in production

`docker-compose.prod.yml` ships with floating tags so a fresh self-hoster gets a
working stack. In production, pin them, so `docker compose pull` can never restart
your database on a minor version you did not choose:

```bash
docker exec bottlevault-db postgres --version
docker inspect --format '{{.Config.Image}}' bottlevault-tunnel
```

Set `POSTGRES_IMAGE` and `CLOUDFLARED_IMAGE` in the Dockge environment to the
exact versions those report, and bump them deliberately. Postgres **minor**
upgrades (16.x → 16.y) need no dump/restore; **major** upgrades do — see the
classification table above.

---

## 2. Where your data lives (why safe updates are safe)

Nothing user-facing is stored inside a container. Per
[`docker-compose.prod.yml`](docker-compose.prod.yml):

- **Database** → host bind mount `./data/postgres`
  (on TrueNAS: `/mnt/<pool>/configs/stacks/bottlevault/data/postgres`)
- **Bottle photos** → host bind mount `./data/uploads`
  (on TrueNAS: `/mnt/<pool>/configs/stacks/bottlevault/data/uploads`)
- **Web request logs** → host bind mount `./data/logs/nginx`
  (on TrueNAS: `/mnt/<pool>/configs/stacks/bottlevault/data/logs/nginx`)
- **Sessions** are stateless JWTs validated by the backend — restarting
  containers does **not** log anyone out.

Pulling a new image and recreating a container never touches those directories.
The only way to lose data is to delete the bind mounts (or the dataset). So:

> **Never** run `docker compose down -v`, and never delete the stack in a way
> that offers to remove volumes or the `./data` directory. Image pulls are
> always safe; deleting data is the only destructive act.

**What the access log contains.** nginx records a line per external request:
timestamp, request line, status, bytes sent, referer, user-agent, and — when
Cloudflare is in front — its two-letter country header as `cc=`. **No IP
addresses are recorded in the access log.** Requests from the container's own
healthcheck arrive on loopback and are excluded. Without Cloudflare — or for
any client that reaches the published port directly instead of through
Cloudflare — the `cc=` field reads `-`, not empty, and the line is otherwise
an ordinary access-log entry.

The country field is only as trustworthy as the ingress path: nothing stops a
client that reaches nginx directly, bypassing Cloudflare, from setting its own
`CF-IPCountry` header. Read `cc=` values with more caution on a deployment
reachable outside the tunnel.

No rotation is configured, so the log directory grows without bound. Lines are
small — a few hundred bytes per request at most — but check the directory
occasionally, not just the access log: `error.log` shares the mount and grows
fastest during exactly the outages you'd want to notice.

```bash
du -h data/logs/nginx/
```

To turn request logging off entirely, remove the `volumes:` block from the
`frontend` service; logs then go to `docker logs` and are discarded when the
container is rebuilt.

If `frontend` won't come up after setting `WEB_LOG_PATH`, check
`docker logs bottlevault-web` for an nginx permission error on
`/var/log/nginx` — an unwritable or misconfigured log path makes nginx exit at
startup. Because `tunnel` waits on `frontend: condition: service_healthy`, a
bad path won't just log an error; it stalls a cold `docker compose up` at the
tunnel gate and can look like a hang.

**Error logs.** The same mount replaces nginx's `error.log -> /dev/stderr`
symlink, so runtime nginx errors go to `data/logs/nginx/error.log` rather than
`docker logs`. Startup failures still reach `docker logs`, because they happen
before the configuration is loaded. Unlike the access log, error-log entries
can include a client network address: behind a tunnel that's the internal
tunnel container's address (harmless), but on a deployment that publishes a
host port directly, it can be the client's own address.

---

## 3. Standard safe update (✅ rows)

**Dockge:** open the `bottlevault` stack → **Update** (Dockge does `pull` +
`up -d`, recreating only containers whose image changed).

**CLI equivalent:**

```bash
cd /mnt/<pool>/configs/stacks/bottlevault
# Pull only the app images so the pinned postgres:16-alpine container isn't churned.
docker compose -f docker-compose.prod.yml pull backend frontend
docker compose -f docker-compose.prod.yml up -d
```

Startup ordering is handled by the compose healthchecks: `backend` waits for
Postgres healthy, `frontend` waits for `backend` healthy.

**Verify after updating:**
1. Hard-reload the app (users must reload — an open tab keeps running the old
   assets until refreshed).
2. Open an existing bottle to confirm data is intact.
3. Spot-check whatever the update changed.

---

## 4. Back up before ⚠️ / 🛑 updates

Two backup methods. **The ZFS snapshot is the primary one** because it captures
the database *and* the uploads directory atomically — a `pg_dump` alone misses
the bottle photos.

### ZFS snapshot (recommended — full, atomic, fast to roll back)

TrueNAS UI: **Datasets → the `bottlevault` stack dataset → Create Snapshot**,
with **Recursive** ticked. Or on the shell:

```bash
zfs snapshot -r <pool>/configs/stacks/bottlevault@pre-update-$(date +%Y%m%d-%H%M)
```

**The `-r` is not optional if you followed the advice in
[`docker-compose.prod.yml`](docker-compose.prod.yml) and put the database on its
own dataset** — and the same applies if uploads live on one. `zfs snapshot`
without `-r` captures a single dataset, so on that layout it would snapshot the
parent alone and neither the database nor the photos. The command still reports
success. You would discover the backup was empty at the moment you needed it.

Recursive snapshots are taken atomically, so DB and photos still correspond to
the same instant. Rollback is where the split layout costs you something —
see §6.

### Logical DB dump (secondary — DB only, portable)

```bash
docker exec bottlevault-db pg_dump -U bottlevault --clean --if-exists bottlevault \
  > bottlevault-$(date +%Y%m%d-%H%M).sql
```

`--clean --if-exists` makes the dump self-restoring (it drops objects before
recreating them). This does **not** include `./data/uploads`; copy that
directory separately if you rely on the dump alone.

---

## 5. Migration-bearing updates (⚠️ / 🛑 rows)

1. **Back up** (§4) — non-negotiable. Flyway migrations are forward-only; a bad
   one is the only thing that can corrupt or lose data.
2. **Test the migration off prod first.** Trigger CI `workflow_dispatch` with a
   `tag` input to publish `:<tag>` images, stand up a scratch stack pointed at
   `:<tag>` against a **copy** of the data, and confirm Flyway applies cleanly
   and the app still reads existing rows. (This is the dev/prod split described
   in CLAUDE.md's deployment section.)
3. **Deploy to prod** in a quiet window using the standard update (§3). On
   `api` startup Flyway applies the new `V*.sql` before the healthcheck passes.
4. **Confirm** the schema change landed and existing data survived, then re-run
   the §3 verify steps.

---

## 6. Rollback

**Bad image / app-level regression (no migration):** repoint the stack at the
previous image tag and `up -d`. Prefer deploying by immutable `:<tag>` over
`:latest` when you need a known-good target to fall back to.

**Bad migration or data corruption:** roll back the ZFS snapshot taken in §4.

**If the database and uploads are on their own datasets, the single command
below is not enough** — use the per-dataset commands that follow it instead.

```bash
docker compose -f docker-compose.prod.yml down          # stop writers first (NO -v)
# Only sufficient on an undivided stack dataset. If postgres/uploads have
# their own datasets, use the per-dataset commands below instead.
zfs rollback <pool>/configs/stacks/bottlevault@pre-update-YYYYMMDD-HHMM
docker compose -f docker-compose.prod.yml up -d
```

`zfs rollback` has no option to descend into child datasets — its `-r` and
`-R` flags mean "destroy newer snapshots", not "recurse", which is an easy and
expensive thing to misread. Roll back each dataset that holds data, naming the
same snapshot on each:

```bash
docker compose -f docker-compose.prod.yml down          # stop writers first (NO -v)
zfs rollback <pool>/configs/stacks/bottlevault/data/postgres@pre-update-YYYYMMDD-HHMM
zfs rollback <pool>/configs/stacks/bottlevault/data/uploads@pre-update-YYYYMMDD-HHMM
docker compose -f docker-compose.prod.yml up -d
```

Unlike the snapshot, this is **not atomic** — the datasets are restored one at a
time. That is harmless here because every writer is stopped first, which is why
`down` comes before the rollbacks rather than after. Do not skip it.

**If any snapshot newer than the target exists, every command above will
refuse** with `cannot rollback to '...': more recent snapshots or bookmarks
exist`. TrueNAS periodic snapshot tasks make this the common case, not the
exception. The fix is `-r` on the *rollback* — unlike `-r` on `zfs snapshot`,
which means "recurse into child datasets", `-r` on `zfs rollback` destroys
every snapshot newer than the target. That is the intended outcome here: you
are deliberately discarding everything created after the pre-update snapshot
in order to revert to it.

List what you actually have before rolling back, so a dataset you forgot does
not survive the restore holding post-migration data:

```bash
zfs list -r -t snapshot <pool>/configs/stacks/bottlevault
```

Or, from a logical dump:

```bash
docker compose -f docker-compose.prod.yml stop backend  # stop writers
docker exec -i bottlevault-db psql -U bottlevault -d bottlevault \
  < bottlevault-YYYYMMDD-HHMM.sql
docker compose -f docker-compose.prod.yml start backend
```

Rolling back the app image **below** the current schema version is not safe if a
migration has since run — restore the matching data snapshot too.

---

## 7. Container / path reference

| Thing | Value |
|---|---|
| DB container | `bottlevault-db` (image `postgres:16-alpine`, pinned) |
| DB name / user | `bottlevault` / `bottlevault` |
| API container | `bottlevault-api` (`ghcr.io/toastedcoffee/bottlevault-api:latest`) |
| Web container | `bottlevault-web` (`ghcr.io/toastedcoffee/bottlevault-web:latest`) |
| Tunnel container | `bottlevault-tunnel` |
| DB data (host) | `<stack>/data/postgres` |
| Uploads (host) | `<stack>/data/uploads` |
| Web logs (host) | `<stack>/data/logs/nginx` |
| Migrations (repo) | `backend/src/main/resources/db/migration/V*.sql` and `backend/src/main/kotlin/db/migration/V*.kt` (Flyway JVM migrations) |
| Stack dir (TrueNAS) | `/mnt/<pool>/configs/stacks/bottlevault` |

---

## 7a. Pre-check before the name normalization migration (V7 to V9)

V9 adds a `UNIQUE` constraint on a normalized form of every brand name, and a
`UNIQUE(brand_id, normalized_name)` constraint on product names within a
brand. If two rows normalize to the same value, the migration fails partway
through and the API will not start. Run both checks below **before**
updating — against the `name` column, since this runs pre-V7, before the
rename to `display_name` — and merge or rename anything they return.

Normalization must match `NameNormalizer.normalize` in the Kotlin codebase:
trim, fold diacritics, strip anything that isn't a letter/digit/space,
collapse whitespace, lowercase. This check can only ever *approximate* that
in SQL, and the two will never match exactly. That mismatch is tolerable in
only one direction: this check must be **strictly coarser** than
`NameNormalizer`, so it over-reports rather than under-reports. A false
positive just costs a few minutes checking a pair that turns out fine; a
false clean means the migration itself discovers the problem partway
through, with the API down.

For whitespace, the query below achieves that. Postgres's `\s` / `[:space:]`
does not agree with the normalizer's own notion of whitespace, which
explicitly folds U+00A0 (non-breaking space) and U+200B (zero-width space) as
a deliberate, documented feature, not just ordinary spaces and tabs. So
rather than try to enumerate every character Postgres and the normalizer
might disagree on being "whitespace," this check strips every character that
isn't a letter or digit outright (instead of collapsing whitespace to a
single space) — that one change subsumes ordinary spaces, non-breaking
spaces, zero-width spaces, tabs, and doubled spaces alike, since none of them
survive to be compared.

For diacritics, it does **not** achieve that, and this is a real gap rather
than a theoretical one. Below, `translate()` only *folds* the ~50 accented
Latin-1 characters it explicitly lists (real cases in this catalogue:
`Patrón` -> `patron`, `Rémy Martin` -> `remy martin`) — it runs instead of the
`unaccent` extension since `unaccent` may not be installed. Any character
outside that list is not folded at all; it is *deleted* by the
`regexp_replace(..., '[^a-zA-Z0-9]', '', 'g')` that follows, because by that
point it is still not a plain ASCII letter. Deletion and folding are
different mappings, so two rows that `NameNormalizer` (which folds *every*
Unicode diacritic via NFD, not just the Latin-1 ones) considers a collision
can land in different buckets here and be reported clean. Concrete case:
brands `Jūnmai` and `Junmai` both normalize to `junmai` in Kotlin, so V9
rejects the pair — but this query turns `Jūnmai` into `jnmai` (the `ū` is
deleted, not folded to `u`) versus `junmai` for the plain spelling: different
values, zero rows, reported clean. `İzmir`/`Izmir` and `Bāng`/`Bang` fail the
same way. This is reachable today, not hypothetical: `SAKE` is a supported
`AlcoholType` and the catalogue already contains a sake brewery. Both queries
run against production PostgreSQL only, never H2, so the Postgres-only
`translate()` is fine here — unlike the migrations themselves.

**Do not "fix" this by extending the `translate()` character list.** That
only moves the boundary to wherever the list still doesn't reach — the next
unlisted accented character is exactly as invisible to the query as `ū` is
today. Instead, run the third query further below against both tables and
hand-check every row it returns: those are exactly the rows carrying a
character the first two queries cannot fold correctly, so they are the only
place the "strictly coarser" property above can silently fail.

Brands:

```bash
docker exec -i bottlevault-db psql -U bottlevault -d bottlevault -c "SELECT lower(regexp_replace(translate(trim(name), 'áàâäãåéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÅÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ', 'aaaaaaeeeeiiiiooooouuuuncAAAAAAEEEEIIIIOOOOOUUUUNC'), '[^a-zA-Z0-9]', '', 'g')) AS normalized, count(*) AS variants, string_agg(name, ' | ') AS spellings FROM brands GROUP BY 1 HAVING count(*) > 1;"
```

Products — grouped per `brand_id`, since the V9 constraint is scoped to a
brand and the same product name is allowed to repeat across different
brands. Check this one too: V1 never put a uniqueness constraint on
`products.name`, so a duplicate here is considerably more likely than a
duplicate brand:

```bash
docker exec -i bottlevault-db psql -U bottlevault -d bottlevault -c "SELECT brand_id, lower(regexp_replace(translate(trim(name), 'áàâäãåéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÅÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ', 'aaaaaaeeeeiiiiooooouuuuncAAAAAAEEEEIIIIOOOOOUUUUNC'), '[^a-zA-Z0-9]', '', 'g')) AS normalized, count(*) AS variants, string_agg(name, ' | ') AS spellings FROM products GROUP BY 1, 2 HAVING count(*) > 1;"
```

Companion check — every row whose name contains a character outside the
printable ASCII range, in either table. These are the rows the two checks
above cannot be trusted on: any of them might fold to a collision under
`NameNormalizer` that the `translate()`-based checks miss entirely, as with
`Jūnmai`/`Junmai` above. Nothing here is a pass/fail signal by itself — treat
every returned row as a prompt to manually work out what `NameNormalizer.normalize`
would produce for it (or just check the app's own brand/product search once
the name is in) and compare against the rest of the catalogue in the same
scope (all brands; products within the same `brand_id`):

```bash
docker exec -i bottlevault-db psql -U bottlevault -d bottlevault -c "SELECT 'brand' AS table_name, id::text, name FROM brands WHERE name ~ '[^\x20-\x7E]' UNION ALL SELECT 'product', id::text || ' (brand ' || brand_id || ')', name FROM products WHERE name ~ '[^\x20-\x7E]' ORDER BY 1, 3;"
```

Zero rows from the first two, and a clean hand-check of anything the third
returns, means V7 to V9 will apply cleanly. If you skip this and
two brands differ only by whitespace or diacritics, V8 fails while rewriting
`display_name` in place — against the **old** `brands_name_key` constraint
inherited from V1, not the new `uq_brands_normalized_name` V9 adds. Don't be
misled by the constraint name in the error: it's the same class of problem
as above, and the same fix (normalize, merge or rename, re-run).

Because this check is deliberately coarser than `NameNormalizer`, it can also
flag pairs that would not actually collide — for example `Brewery & Kitchen`
versus `BreweryKitchen`. The real normalizer keeps those distinct: it strips
the `&` but preserves the space around it, so one normalizes with a
word-separating space and the other without. This check strips both the `&`
and every space, so the two collapse to the same value here even though V9
would accept them both. Investigate anything this reports — don't assume a
report is a false alarm without checking — but expect the occasional one; a
little extra investigation is the price of never seeing a false "clean."

This is a **Class B**, backup-required update: it renames columns and takes
2 to 5 minutes of downtime.

**Also verify the V8 migration class actually shipped in the image.** V8 is
a Kotlin `BaseJavaMigration` (package `db.migration`), found on the classpath
at startup. The test suite only proves this works from exploded classes, not
from inside the Spring Boot fat jar — if it were silently missing there, V8
would be skipped and V9's `SET NOT NULL` would abort on NULL rows. Loud, but
only at deploy time, so confirm first. Either check the jar directly:

```bash
docker run --rm --entrypoint sh <image> -c 'unzip -l app.jar | grep V8__'
```

or watch the container log during the update for Flyway's line:
`Migrating schema "public" to version "8"`.

---

## 7b. Populating brand aliases (manual)

`brands.aliases` and `brands.normalized_aliases` exist so a search for one
term can surface a brand that term never actually appears on — the
motivating case is searching "Suntory" and getting `Hibiki`, even though the
word "Suntory" appears in none of Hibiki's own name fields or its products'.
There is no API field for this: populating aliases through the app is
deliberately out of scope for this phase (a maintainer script for it is
planned for later), so the only route today is a manual SQL update run
directly against the row.

Both columns must be set together. `aliases` is the human-readable list;
`normalized_aliases` is what brand search actually matches against (the same
`LIKE`-based query that reads `normalized_name` also reads this column), so
setting `aliases` alone silently yields no search benefit and raises no
error. The value in `normalized_aliases` must match what
`NameNormalizer.normalize` would produce for that word, not whatever casing
or spacing you typed into `aliases`: lowercase, no punctuation, single
spaces.

Worked example — adding "Suntory" as an alias of the brand `Hibiki`:

```bash
docker exec -i bottlevault-db psql -U bottlevault -d bottlevault -c "UPDATE brands SET aliases = 'Suntory', normalized_aliases = 'suntory' WHERE normalized_name = 'hibiki';"
```

Confirm it landed on the row you meant:

```bash
docker exec -i bottlevault-db psql -U bottlevault -d bottlevault -c "SELECT display_name, aliases, normalized_aliases FROM brands WHERE normalized_name = 'hibiki';"
```

---

## 8. Future hardening (not yet built)

- **Zero-downtime:** two `web` replicas or a blue/green swap behind the tunnel,
  removing the recreate blip on ✅ updates.
