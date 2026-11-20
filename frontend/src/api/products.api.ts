// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import apiClient from './client';
import type { ProductResponse, ProductCreateRequest, AlcoholType } from '../types/product';

export const productsApi = {
  /** At most 20 products. The server needs a brandId or a term of 2+ letters or digits. */
  list: (params: { brandId?: string; type?: AlcoholType; search?: string }) =>
    apiClient.get<ProductResponse[]>('/products', { params }).then((r) => r.data),

  /** "Did you mean": up to five products of one brand near `name`, never the same name. */
  suggest: (brandId: string, name: string) =>
    apiClient.get<ProductResponse[]>('/products/suggest', { params: { brandId, name } }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<ProductResponse>(`/products/${id}`).then((r) => r.data),

  create: (data: ProductCreateRequest) =>
    apiClient.post<ProductResponse>('/products', data).then((r) => r.data),
};
