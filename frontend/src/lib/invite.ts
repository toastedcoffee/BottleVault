// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee

/**
 * The invite code from an invite link (…/register#invite=CODE). The code rides
 * after '#', which browsers never send to the server, so it cannot land in a
 * server log.
 */
export function readInviteFromHash(hash: string): string | null {
  const match = /(?:^#|&)invite=([^&]*)/.exec(hash);
  if (!match) return null;
  try {
    return decodeURIComponent(match[1] ?? '');
  } catch {
    return null;
  }
}
