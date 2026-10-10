/** Central query-key factory so invalidation stays consistent across features. */
export const queryKeys = {
  dashboard: ['dashboard'] as const,
  deposits: {
    all: ['deposits'] as const,
    list: (status?: string) => ['deposits', 'list', status ?? 'ALL'] as const,
    detail: (id: string) => ['deposits', 'detail', id] as const,
    quote: (id: string) => ['deposits', 'quote', id] as const,
  },
  loans: {
    all: ['loans'] as const,
    list: (direction?: string, status?: string) => ['loans', 'list', direction ?? 'ALL', status ?? 'ALL'] as const,
    detail: (id: string) => ['loans', 'detail', id] as const,
  },
  notifications: ['notifications'] as const,
  profile: ['profile'] as const,
};
