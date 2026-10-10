import { http } from '@/shared/api/http';
import type { Dashboard } from '@/shared/api/types';

export const dashboardApi = {
  get: () => http.get<Dashboard>('/api/v1/dashboard'),
};
