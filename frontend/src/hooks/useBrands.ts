import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { brandsApi } from '../api/brands.api';
import type { BrandCreateRequest } from '../types/brand';

export function useBrands(search?: string) {
  return useQuery({
    queryKey: ['brands', search],
    queryFn: () => brandsApi.list(search),
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
