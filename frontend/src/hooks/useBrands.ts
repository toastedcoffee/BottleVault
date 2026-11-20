// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { brandsApi } from '../api/brands.api';
import type { BrandCreateRequest } from '../types/brand';
import { isSearchable } from '../lib/catalogSearch';

/** Brands matching `term`; idle until the term holds two letters or digits. */
export function useBrandSearch(term: string) {
  const search = term.trim();
  return useQuery({
    queryKey: ['brands', 'search', search],
    queryFn: () => brandsApi.search(search),
    enabled: isSearchable(search),
  });
}

/** One brand by id, for showing a selection the picker did not make itself. */
export function useBrand(id: string) {
  return useQuery({
    queryKey: ['brands', 'byId', id],
    queryFn: () => brandsApi.getById(id),
    enabled: id !== '',
  });
}

/** "Did you mean" for a brand name; idle while `name` is null. */
export function useBrandSuggestions(name: string | null) {
  return useQuery({
    queryKey: ['brands', 'suggest', name],
    queryFn: () => brandsApi.suggest(name ?? ''),
    enabled: name !== null,
  });
}

export function useCreateBrand() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: BrandCreateRequest) => brandsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['brands'] });
    },
  });
}
