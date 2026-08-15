// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import type { ProductResponse } from './product';

export interface BarcodeLookupResponse {
  found: boolean;
  source: string | null;
  product: ProductResponse | null;
  externalProduct: ExternalProductData | null;
}

export interface ExternalProductData {
  name: string;
  brandName: string | null;
  barcode: string;
  size: string | null;
  abv: number | null;
  imageUrl: string | null;
  categories: string | null;
}
