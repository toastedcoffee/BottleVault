import apiClient from './client';
import type { BottleResponse, BottleCreateRequest, BottleUpdateRequest, PageResponse } from '../types/bottle';
import type { BottleStatus } from '../types/bottle';

export interface BottleQueryParams {
  status?: BottleStatus;
  type?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const bottlesApi = {
  list: (params: BottleQueryParams = {}) =>
    apiClient.get<PageResponse<BottleResponse>>('/bottles', { params }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<BottleResponse>(`/bottles/${id}`).then((r) => r.data),

  create: (data: BottleCreateRequest) =>
    apiClient.post<BottleResponse>('/bottles', data).then((r) => r.data),

  update: (id: string, data: BottleUpdateRequest) =>
    apiClient.put<BottleResponse>(`/bottles/${id}`, data).then((r) => r.data),

  updateStatus: (id: string, status: BottleStatus) =>
    apiClient.patch<BottleResponse>(`/bottles/${id}/status`, { status }).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete(`/bottles/${id}`),
};
