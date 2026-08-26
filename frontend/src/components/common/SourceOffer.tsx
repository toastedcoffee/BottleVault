// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee

const UPSTREAM_URL = 'https://github.com/toastedcoffee/BottleVault';

/**
 * Resolve where the source offer should point. Defaults to upstream; a
 * self-hoster who *modifies* the code sets VITE_SOURCE_URL (Docker build arg
 * SOURCE_URL) to their own repository so the link discharges *their* section 13
 * duty rather than pointing at code they are not running.
 *
 * Anything unparseable, or any scheme other than http(s), falls back to
 * upstream. A silently broken link is the failure mode this whole mechanism
 * exists to prevent, so a bad value must not render as-is.
 *
 * Rebuilt from origin + pathname rather than returned verbatim, because
 * `/tree/<sha>` is appended to whatever comes back. A pasted URL carrying a
 * query or fragment would otherwise absorb that suffix — `...repo#readme`
 * becomes `...repo#readme/tree/<sha>`, which the browser resolves to the
 * default branch while silently discarding the commit pin.
 */
function resolveSourceUrl(raw: string | undefined): string {
  const candidate = (raw ?? '').trim();
  if (!candidate) return UPSTREAM_URL;

  try {
    const parsed = new URL(candidate);
    // `new URL` accepts javascript: and data: without throwing.
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return UPSTREAM_URL;
    return `${parsed.origin}${parsed.pathname}`.replace(/\/+$/, '');
  } catch {
    return UPSTREAM_URL;
  }
}

/**
 * AGPL section 13 compliance: users interacting with this app over a
 * network must be offered the *corresponding* source. The commit SHA is baked
 * in at build time (Docker build arg GIT_SHA -> VITE_GIT_SHA), so the link
 * resolves to the exact tree this instance runs. Builds without the arg — local
 * dev, or a self-hoster running docker-compose.yml directly — fall back to the
 * repo root, which is honest rather than misleading.
 *
 * This is the frontend image's SHA. CI tags both images from the same commit,
 * so it represents the deployment; a self-hoster running mismatched image tags
 * could see a value that does not describe their backend.
 *
 * The repository is likewise build-time configurable (SOURCE_URL ->
 * VITE_SOURCE_URL), defaulting to upstream. A self-hoster who modifies the code
 * must set *both*: SOURCE_URL alone yields their repo root, but GIT_SHA alone
 * pins their commit against the upstream repository, which 404s.
 *
 * This link is a licence obligation. Do not remove it.
 */
export default function SourceOffer() {
  const sha = (import.meta.env.VITE_GIT_SHA ?? '').trim() || 'dev';
  const isPinned = sha !== 'dev';
  const repoUrl = resolveSourceUrl(import.meta.env.VITE_SOURCE_URL);

  return (
    <p className="text-xs text-text-low text-center">
      BottleVault — AGPL-3.0-only —{' '}
      <a
        href={isPinned ? `${repoUrl}/tree/${sha}` : repoUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="underline hover:text-text-mid"
      >
        Source
      </a>
      {isPinned && <span className="ml-1 font-mono">{sha.slice(0, 7)}</span>}
    </p>
  );
}
