import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/shared/api/queryKeys';
import type { CreateLoanRequest, LoanDirection, LoanStatus } from '@/shared/api/types';
import { loanApi } from './api';

export function useLoans(direction?: LoanDirection, status?: LoanStatus) {
  return useQuery({ queryKey: queryKeys.loans.list(direction, status), queryFn: () => loanApi.list(direction, status) });
}

export function useLoan(id: string) {
  return useQuery({ queryKey: queryKeys.loans.detail(id), queryFn: () => loanApi.get(id) });
}

function useInvalidateLoans() {
  const client = useQueryClient();
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: queryKeys.loans.all }),
      client.invalidateQueries({ queryKey: queryKeys.dashboard }),
    ]);
}

export function useCreateLoan() {
  const invalidate = useInvalidateLoans();
  return useMutation({ mutationFn: (request: CreateLoanRequest) => loanApi.create(request), onSuccess: invalidate });
}

export function useSchedulePreview() {
  return useMutation({ mutationFn: (request: CreateLoanRequest) => loanApi.previewSchedule(request) });
}

export function useRepayLoan(id: string) {
  const invalidate = useInvalidateLoans();
  return useMutation({
    mutationFn: (input: { amount: number; paidOn: string; idempotencyKey: string }) =>
      loanApi.repay(id, input.amount, input.paidOn, input.idempotencyKey),
    onSuccess: invalidate,
  });
}

export function usePrepayLoan(id: string) {
  const invalidate = useInvalidateLoans();
  return useMutation({
    mutationFn: (input: { amount: number; paidOn: string; idempotencyKey: string }) =>
      loanApi.prepay(id, input.amount, input.paidOn, input.idempotencyKey),
    onSuccess: invalidate,
  });
}

export function useChangeLoanRate(id: string) {
  const invalidate = useInvalidateLoans();
  return useMutation({
    mutationFn: (input: { effectiveFrom: string; annualRate: number }) =>
      loanApi.changeRate(id, input.effectiveFrom, input.annualRate),
    onSuccess: invalidate,
  });
}

export function useDeleteLoan(id: string) {
  const invalidate = useInvalidateLoans();
  return useMutation({ mutationFn: () => loanApi.remove(id), onSuccess: invalidate });
}
