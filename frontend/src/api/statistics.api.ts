// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import apiClient from './client';
import type { StatisticsResponse } from '../types/statistics';

export const statisticsApi = {
  get: () => apiClient.get<StatisticsResponse>('/statistics').then((r) => r.data),
};
