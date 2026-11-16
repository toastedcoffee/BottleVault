// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useQuery } from '@tanstack/react-query';
import { authApi } from '../api/auth.api';

/** Registration mode, Turnstile site key and privacy link, read at runtime. */
export function useAuthConfig() {
  return useQuery({ queryKey: ['auth-config'], queryFn: authApi.config, staleTime: 60_000, retry: 1 });
}
