import apiClient from './client';
import type { StatisticsResponse } from '../types/statistics';

export const statisticsApi = {
  get: () => apiClient.get<StatisticsResponse>('/statistics').then((r) => r.data),
};
