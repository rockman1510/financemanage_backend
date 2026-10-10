import type { TFunction } from 'i18next';
import { env } from '../config/env';
import { ApiError } from './ApiError';
import { NETWORK_ERROR } from './client';

/** User-facing message for any error thrown by the API layer. */
export function errorMessage(error: unknown, t: TFunction): string {
  if (error instanceof ApiError) {
    if (error.code === NETWORK_ERROR) {
      return t('common.networkError', { url: env.apiUrl });
    }
    return error.message;
  }
  return t('common.unexpectedError');
}
