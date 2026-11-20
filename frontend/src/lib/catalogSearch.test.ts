// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect } from 'vitest';
import { isSearchable } from './catalogSearch';

describe('isSearchable', () => {
  it('needs two letters or decimal digits', () => {
    expect(isSearchable('ja')).toBe(true);
    expect(isSearchable('12')).toBe(true);
    expect(isSearchable('é1')).toBe(true);
    expect(isSearchable('j')).toBe(false);
    expect(isSearchable('a!')).toBe(false);
    expect(isSearchable('&&')).toBe(false);
  });

  it('does not count numerals the server drops', () => {
    expect(isSearchable('a²')).toBe(false);
  });
});
