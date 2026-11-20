// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee

/**
 * Whether a catalog list request is worth sending: the server refuses a term with
 * fewer than two letters or digits. This only counts characters to avoid a request
 * that would be refused anyway; the server's normalizer decides what matches.
 */
export function isSearchable(term: string): boolean {
  // Decimal digits only (\p{Nd}): the server drops "²" and other non-decimal numerals.
  return (term.match(/[\p{L}\p{Nd}]/gu)?.length ?? 0) >= 2;
}
