// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee

const REPO_URL = 'https://github.com/toastedcoffee/BottleVault';

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
 * This link is a licence obligation. Do not remove it.
 */
export default function SourceOffer() {
  const sha = import.meta.env.VITE_GIT_SHA || 'dev';
  const isPinned = sha !== 'dev';

  return (
    <p className="text-xs text-text-low text-center">
      BottleVault — AGPL-3.0-only —{' '}
      <a
        href={isPinned ? `${REPO_URL}/tree/${sha}` : REPO_URL}
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
