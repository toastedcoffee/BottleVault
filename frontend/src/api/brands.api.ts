import apiClient from './client';
import type { BrandResponse, BrandCreateRequest } from '../types/brand';

export const brandsApi = {
  list: (search?: string) =>
    apiClient.get<BrandResponse[]>('/brands', { params: search ? { search } : {} }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<BrandResponse>(`/brands/${id}`).then((r) => r.data),

  create: (data: BrandCreateRequest) =>
    apiClient.post<BrandResponse>('/brands', data).then((r) => r.data),
};
