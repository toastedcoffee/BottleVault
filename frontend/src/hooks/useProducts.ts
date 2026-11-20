// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { productsApi } from '../api/products.api';
import type { ProductCreateRequest } from '../types/product';

/** One brand's products, narrowed by `term` when it is not blank; idle without a brand. */
export function useProductSearch(brandId: string, term: string) {
  const search = term.trim();
  return useQuery({
    queryKey: ['products', 'search', brandId, search],
    queryFn: () => productsApi.list(search ? { brandId, search } : { brandId }),
    enabled: brandId !== '',
  });
}

/** One product by id, for showing a selection the picker did not make itself. */
export function useProduct(id: string) {
  return useQuery({
    queryKey: ['products', 'byId', id],
    queryFn: () => productsApi.getById(id),
    enabled: id !== '',
  });
}

/** "Did you mean" for a product name within one brand; idle while either is missing. */
export function useProductSuggestions(brandId: string | null, name: string | null) {
  return useQuery({
    queryKey: ['products', 'suggest', brandId, name],
    queryFn: () => productsApi.suggest(brandId ?? '', name ?? ''),
    enabled: brandId !== null && brandId !== '' && name !== null,
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: ProductCreateRequest) => productsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });
}
