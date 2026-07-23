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
git diff --name-only <deployed-sha>..<new-sha> -- backend/src/main/resources/db/migration/

# Which parts changed at all?
git diff --stat <deployed-sha>..<new-sha>
```

A migration file under `backend/src/main/resources/db/migration/` is the single
signal that a backup is required. Whether it's "additive" or "destructive" is a
judgement call — read the SQL: `CREATE TABLE`/`ADD COLUMN`/`CREATE INDEX` is
additive; `DROP`, `ALTER ... TYPE`, `RENAME`, or any `UPDATE`/`DELETE` backfill
is destructive and needs the full-care path.

---

## 2. Where your data lives (why safe updates are safe)

Nothing user-facing is stored inside a container. Per
[`docker-compose.prod.yml`](docker-compose.prod.yml):

- **Database** → host bind mount `./data/postgres`
  (on TrueNAS: `/mnt/<pool>/configs/stacks/bottlevault/data/postgres`)
- **Bottle photos** → host bind mount `./data/uploads`
  (on TrueNAS: `/mnt/<pool>/configs/stacks/bottlevault/data/uploads`)
- **Sessions** are stateless JWTs validated by the backend — restarting
  containers does **not** log anyone out.

Pulling a new image and recreating a container never touches those directories.
The only way to lose data is to delete the bind mounts (or the dataset). So:

> **Never** run `docker compose down -v`, and never delete the stack in a way
> that offers to remove volumes or the `./data` directory. Image pulls are
> always safe; deleting data is the only destructive act.

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

TrueNAS UI: **Datasets → the `bottlevault` stack dataset → Create Snapshot.**
Or on the shell:

```bash
zfs snapshot <pool>/configs/stacks/bottlevault@pre-update-$(date +%Y%m%d-%H%M)
```

Rollback (see §6) restores DB and photos together, keeping them consistent.

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

```bash
docker compose -f docker-compose.prod.yml down          # stop writers first (NO -v)
zfs rollback <pool>/configs/stacks/bottlevault@pre-update-YYYYMMDD-HHMM
docker compose -f docker-compose.prod.yml up -d
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
| Migrations (repo) | `backend/src/main/resources/db/migration/V*.sql` |
| Stack dir (TrueNAS) | `/mnt/<pool>/configs/stacks/bottlevault` |

---

## 8. Future hardening (not yet built)

- **CI gate:** a job that detects a new `backend/src/main/resources/db/migration/V*.sql`
  in a PR diff and labels it `requires-backup` + comments this runbook's §4/§5,
  so the backup step can't be forgotten.
- **Zero-downtime:** two `web` replicas or a blue/green swap behind the tunnel,
  removing the recreate blip on ✅ updates.
