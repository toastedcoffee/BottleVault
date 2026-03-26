import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { bottlesApi, type BottleQueryParams } from '../api/bottles.api';
import type { BottleCreateRequest, BottleUpdateRequest, BottleStatus } from '../types/bottle';

export function useBottles(params: BottleQueryParams = {}) {
  return useQuery({
    queryKey: ['bottles', params],
    queryFn: () => bottlesApi.list(params),
  });
}

export function useBottle(id: string) {
  return useQuery({
    queryKey: ['bottles', id],
    queryFn: () => bottlesApi.getById(id),
    enabled: !!id,
  });
}

export function useCreateBottle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: BottleCreateRequest) => bottlesApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bottles'] });
    },
  });
}

export function useUpdateBottle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: BottleUpdateRequest }) =>
      bottlesApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bottles'] });
    },
  });
}

export function useUpdateBottleStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: BottleStatus }) =>
      bottlesApi.updateStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bottles'] });
    },
  });
}

export function useDeleteBottle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => bottlesApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bottles'] });
    },
  });
}
