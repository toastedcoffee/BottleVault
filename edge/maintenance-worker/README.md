# BottleVault maintenance Worker

Sits in front of `cellar.bottlevault.app`. Two jobs:

- **Planned** — a KV flag you flip before a Class B deploy (see [DEPLOY.md](../../DEPLOY.md)).
- **Unplanned** — origin-down responses are converted automatically, no flag needed.
  A cold boot produces a 1033 at the edge until cloudflared connects; this catches it.

`/api/*` gets a JSON 503 that the SPA renders as its own banner. Everything else
gets a branded HTML 503. Serving HTML to an XHR would break the app, which is why
the split exists rather than one blanket response.

Free tier: 100,000 Worker requests/day, 100,000 KV reads/day, 1,000 KV writes/day.
Nowhere near a constraint at beta scale.

## Deploy

```bash
npx wrangler kv namespace create FLAGS     # paste the id into wrangler.toml
npx wrangler deploy
```

## Flip maintenance on and off

```bash
npx wrangler kv key put --binding FLAGS maintenance on
npx wrangler kv key delete --binding FLAGS maintenance
```

Allow up to 60s to propagate (`cacheTtl` in `src/index.js`).

## Verify before trusting it

1. **Access ordering.** Deploy a Worker that unconditionally returns `hello`, then hit
   the hostname from a private window with no Access session. `hello` means Workers
   run first. An Access login means Access runs first — in which case only strangers
   see a login screen during an outage, which is correct behavior; your beta users
   hold live session cookies and land on the maintenance page. Either way, keep the
   canonical status message at `bottlevault.app/status`, outside Access.
2. **The real status codes.** `docker stop bottlevault-tunnel`, hit the hostname, and
   read what the edge actually returns. Add anything missing to `ORIGIN_DOWN` in
   `src/index.js`. Do not trust the list as shipped — it is a superset guess.
3. **The API path.** With the flag on, confirm `/api/bottles` returns JSON and not HTML.
   Serving HTML there is the one failure mode that breaks a running SPA.
