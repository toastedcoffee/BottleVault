// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { describe, it, expect, vi, beforeEach } from 'vitest';
import apiClient from './client';
import { barcodeApi } from './barcode.api';

vi.mock('./client', () => ({ default: { get: vi.fn() } }));

describe('barcodeApi.lookup', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { found: false } });
  });

  it('requests the barcode as a single, encoded path segment', async () => {
    await barcodeApi.lookup('../bottles?size=1');
    expect(apiClient.get).toHaveBeenCalledWith('/barcode/..%2Fbottles%3Fsize%3D1');
  });

  it('leaves an ordinary barcode unchanged', async () => {
    await barcodeApi.lookup('082184000427');
    expect(apiClient.get).toHaveBeenCalledWith('/barcode/082184000427');
  });
});
