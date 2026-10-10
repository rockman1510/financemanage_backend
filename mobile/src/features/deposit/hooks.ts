import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/shared/api/queryKeys';
import type { CreateDepositRequest, DepositStatus } from '@/shared/api/types';
import { depositApi } from './api';

export function useDeposits(status?: DepositStatus) {
  return useQuery({ queryKey: queryKeys.deposits.list(status), queryFn: () => depositApi.list(status) });
}

export function useDeposit(id: string) {
  return useQuery({ queryKey: queryKeys.deposits.detail(id), queryFn: () => depositApi.get(id) });
}

export function useSettlementQuote(id: string, enabled: boolean) {
  return useQuery({ queryKey: queryKeys.deposits.quote(id), queryFn: () => depositApi.settlementQuote(id), enabled });
}

function useInvalidateDeposits() {
  const client = useQueryClient();
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: queryKeys.deposits.all }),
      client.invalidateQueries({ queryKey: queryKeys.dashboard }),
    ]);
}

export function useCreateDeposit() {
  const invalidate = useInvalidateDeposits();
  return useMutation({ mutationFn: (request: CreateDepositRequest) => depositApi.create(request), onSuccess: invalidate });
}

export function useDepositPreview() {
  return useMutation({ mutationFn: (request: CreateDepositRequest) => depositApi.preview(request) });
}

export function useSettleDeposit(id: string) {
  const invalidate = useInvalidateDeposits();
  return useMutation({ mutationFn: () => depositApi.settle(id), onSuccess: invalidate });
}

export function useDeleteDeposit(id: string) {
  const invalidate = useInvalidateDeposits();
  return useMutation({ mutationFn: () => depositApi.remove(id), onSuccess: invalidate });
}
