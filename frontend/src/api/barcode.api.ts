// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import apiClient from './client';
import type { BarcodeLookupResponse } from '../types/barcode';

export const barcodeApi = {
  lookup: (barcode: string) =>
    // Encoded so a scanned value can never become a different path or a query.
    apiClient.get<BarcodeLookupResponse>(`/barcode/${encodeURIComponent(barcode)}`).then((r) => r.data),
};
