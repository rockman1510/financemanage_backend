import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './ApiError';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // client errors (4xx) will not succeed on retry
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
      },
    },
  });
}
