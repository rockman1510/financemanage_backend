import { http } from '@/shared/api/http';
import { queryString } from '@/shared/api/queryString';
import type {
  CreateDepositRequest,
  Deposit,
  DepositPreview,
  DepositStatus,
  SettlementQuote,
} from '@/shared/api/types';

export const depositApi = {
  list: (status?: DepositStatus) => http.get<Deposit[]>(`/api/v1/deposits${queryString({ status })}`),
  get: (id: string) => http.get<Deposit>(`/api/v1/deposits/${id}`),
  create: (request: CreateDepositRequest) => http.post<Deposit>('/api/v1/deposits', request),
  preview: (request: CreateDepositRequest) => http.post<DepositPreview>('/api/v1/deposits/preview', request),
  update: (id: string, changes: { institutionName?: string; accountNumber?: string; note?: string }) =>
    http.patch<Deposit>(`/api/v1/deposits/${id}`, changes),
  remove: (id: string) => http.delete(`/api/v1/deposits/${id}`),
  settlementQuote: (id: string, date?: string) =>
    http.get<SettlementQuote>(`/api/v1/deposits/${id}/settlement-quote${queryString({ date })}`),
  settle: (id: string, date?: string) => http.post<SettlementQuote>(`/api/v1/deposits/${id}/settle`, { date }),
};
