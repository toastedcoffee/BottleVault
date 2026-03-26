import apiClient from './client';
import type { ProductResponse, ProductCreateRequest, AlcoholType } from '../types/product';

export const productsApi = {
  list: (params: { brandId?: string; type?: AlcoholType; search?: string } = {}) =>
    apiClient.get<ProductResponse[]>('/products', { params }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<ProductResponse>(`/products/${id}`).then((r) => r.data),

  create: (data: ProductCreateRequest) =>
    apiClient.post<ProductResponse>('/products', data).then((r) => r.data),
};
