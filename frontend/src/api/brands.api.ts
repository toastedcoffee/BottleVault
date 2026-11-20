// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import apiClient from './client';
import type { BrandResponse, BrandCreateRequest } from '../types/brand';

export const brandsApi = {
  /** At most 20 brands, exact match first. The server needs a term of 2+ letters or digits. */
  search: (search: string) =>
    apiClient.get<BrandResponse[]>('/brands', { params: { search } }).then((r) => r.data),

  /** "Did you mean": up to five brands near `name`, never the same name. */
  suggest: (name: string) =>
    apiClient.get<BrandResponse[]>('/brands/suggest', { params: { name } }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<BrandResponse>(`/brands/${id}`).then((r) => r.data),

  create: (data: BrandCreateRequest) =>
    apiClient.post<BrandResponse>('/brands', data).then((r) => r.data),
};
