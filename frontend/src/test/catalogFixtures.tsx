// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { BrandResponse } from '../types/brand';
import type { ProductResponse } from '../types/product';

/** Renders with a fresh react-query client that does not retry, so a rejected call fails at once. */
export function renderWithQueries(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

export function brand(id: string, displayName: string): BrandResponse {
  return { id, displayName, country: null, website: null };
}

export function product(id: string, displayName: string, b: BrandResponse, size: string | null = null): ProductResponse {
  return {
    id, displayName, barcode: null, type: 'WHISKEY', subtype: null, size, abv: null,
    description: null, imageUrl: null, isUserCreated: false, brand: b,
  };
}
