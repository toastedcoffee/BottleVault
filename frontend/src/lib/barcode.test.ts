// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect } from 'vitest';
import { normalizeBarcodeInput, BARCODE_INPUT_ERROR } from './barcode';

describe('normalizeBarcodeInput', () => {
  it('treats an empty field as no barcode', () => {
    expect(normalizeBarcodeInput('')).toEqual({ valid: true, barcode: undefined });
    expect(normalizeBarcodeInput('   ')).toEqual({ valid: true, barcode: undefined });
  });

  it('strips the spaces and dashes people copy from a label', () => {
    expect(normalizeBarcodeInput('0 82184 00042 7')).toEqual({ valid: true, barcode: '082184000427' });
    expect(normalizeBarcodeInput('5011007-003005')).toEqual({ valid: true, barcode: '5011007003005' });
  });

  it('accepts 8 to 14 digits', () => {
    expect(normalizeBarcodeInput('12345670')).toEqual({ valid: true, barcode: '12345670' });
    expect(normalizeBarcodeInput('12345678901231')).toEqual({ valid: true, barcode: '12345678901231' });
  });

  it('rejects anything else', () => {
    for (const bad of ['1234567', '123456789012345', 'abc12345', 'https://example.com', '1234.5678']) {
      expect(normalizeBarcodeInput(bad)).toEqual({ valid: false });
    }
  });

  it('has copy that says what a barcode is', () => {
    expect(BARCODE_INPUT_ERROR).toBe('Barcode must be 8 to 14 digits (the UPC/EAN number under the bars)');
  });
});
