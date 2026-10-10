import { http } from '@/shared/api/http';
import { queryString } from '@/shared/api/queryString';
import type {
  CreateLoanRequest,
  LoanDetail,
  LoanDirection,
  LoanStatus,
  LoanSummary,
  Repayment,
  SchedulePreview,
} from '@/shared/api/types';

export const loanApi = {
  list: (direction?: LoanDirection, status?: LoanStatus) =>
    http.get<LoanSummary[]>(`/api/v1/loans${queryString({ direction, status })}`),
  get: (id: string) => http.get<LoanDetail>(`/api/v1/loans/${id}`),
  create: (request: CreateLoanRequest) => http.post<LoanDetail>('/api/v1/loans', request),
  previewSchedule: (request: CreateLoanRequest) => http.post<SchedulePreview>('/api/v1/loans/preview-schedule', request),
  remove: (id: string) => http.delete(`/api/v1/loans/${id}`),
  /** The idempotency key makes retries (e.g. flaky network) safe: the server applies the payment once. */
  repay: (id: string, amount: number, paidOn: string, idempotencyKey: string) =>
    http.post<Repayment>(`/api/v1/loans/${id}/repayments`, { amount, paidOn },
      { headers: { 'Idempotency-Key': idempotencyKey } }),
  prepay: (id: string, principal: number, paidOn: string, idempotencyKey: string) =>
    http.post<Repayment>(`/api/v1/loans/${id}/prepayments`, { principal, paidOn },
      { headers: { 'Idempotency-Key': idempotencyKey } }),
  changeRate: (id: string, effectiveFrom: string, annualRate: number) =>
    http.post<LoanDetail>(`/api/v1/loans/${id}/rate-changes`, { effectiveFrom, annualRate }),
};
