// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useQuery } from '@tanstack/react-query';
import { statisticsApi } from '../api/statistics.api';

export function useStatistics() {
  return useQuery({
    queryKey: ['statistics'],
    queryFn: () => statisticsApi.get(),
  });
}
