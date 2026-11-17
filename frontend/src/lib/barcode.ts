// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee

/** UPC-E/EAN-8 through GTIN-14: the only barcodes the server looks up. */
const PRODUCT_BARCODE = /^\d{8,14}$/;

export const BARCODE_INPUT_ERROR =
  'Barcode must be 8 to 14 digits (the UPC/EAN number under the bars)';

export type BarcodeInput = { valid: true; barcode: string | undefined } | { valid: false };

/**
 * Checks a hand-typed barcode before anything is saved. Spaces and dashes are
 * dropped, because people copy the number as printed ("0 82184 00042 7"). An
 * empty field is fine and means "no barcode".
 */
export function normalizeBarcodeInput(raw: string): BarcodeInput {
  const digits = raw.replace(/[\s-]/g, '');
  if (digits === '') return { valid: true, barcode: undefined };
  return PRODUCT_BARCODE.test(digits) ? { valid: true, barcode: digits } : { valid: false };
}
