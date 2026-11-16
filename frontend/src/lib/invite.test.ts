// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect } from 'vitest';
import { readInviteFromHash } from './invite';

describe('readInviteFromHash', () => {
  it('reads the code from an invite link', () => {
    expect(readInviteFromHash('#invite=7K3M-9Q2X-H4TR-8WPA')).toBe('7K3M-9Q2X-H4TR-8WPA');
    expect(readInviteFromHash('#foo=1&invite=AB%2DCD')).toBe('AB-CD');
  });

  it('ignores everything else', () => {
    expect(readInviteFromHash('')).toBeNull();
    expect(readInviteFromHash('#inviter=x')).toBeNull();
    expect(readInviteFromHash('#%E0%A4%A')).toBeNull();
  });
});
