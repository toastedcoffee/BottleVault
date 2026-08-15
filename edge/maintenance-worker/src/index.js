// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import MAINTENANCE_HTML from './maintenance.html';

// Cloudflare surfaces tunnel-down and origin-down as these. 530 is the
// 1xxx-class edge error family, which includes 1033 (tunnel not running) —
// the exact code a cold boot produces before cloudflared connects.
// VERIFY THIS LIST against a deliberate `docker stop bottlevault-tunnel`
// and add whatever you actually observe; see README.
const ORIGIN_DOWN = [502, 503, 504, 521, 522, 523, 525, 526, 530];

const PLANNED_RETRY_SECONDS = 900;
const UNPLANNED_RETRY_SECONDS = 60;

export default {
  async fetch(request, env) {
    const isApi = new URL(request.url).pathname.startsWith('/api/');

    // 503 + Retry-After, never a 200. Crawlers and any monitoring you add later
    // must be able to tell "down on purpose" from "this is the site now."
    const down = (retryAfter) =>
      isApi
        ? Response.json(
            { error: 'maintenance', retryAfter },
            { status: 503, headers: { 'retry-after': String(retryAfter), 'cache-control': 'no-store' } }
          )
        : new Response(MAINTENANCE_HTML, {
            status: 503,
            headers: {
              'content-type': 'text/html; charset=utf-8',
              'retry-after': String(retryAfter),
              'cache-control': 'no-store',
            },
          });

    // Planned: flipped by hand before a Class B deploy. cacheTtl bounds how long
    // the flip takes to propagate; 60s is the floor KV allows.
    if ((await env.FLAGS.get('maintenance', { cacheTtl: 60 })) === 'on') {
      return down(PLANNED_RETRY_SECONDS);
    }

    // Unplanned: caught automatically, no flag needed.
    try {
      // Passthrough to origin, not a loop: Cloudflare detects that this
      // subrequest's URL matches this very Worker's own route and sends it
      // straight to origin instead of re-invoking the Worker. That only
      // holds for a plain fetch(request) on the invoking route — swapping
      // this for a service binding or changing the route pattern would
      // reintroduce a real request loop.
      const response = await fetch(request);
      return ORIGIN_DOWN.includes(response.status) ? down(UNPLANNED_RETRY_SECONDS) : response;
    } catch {
      return down(UNPLANNED_RETRY_SECONDS);
    }
  },
};
