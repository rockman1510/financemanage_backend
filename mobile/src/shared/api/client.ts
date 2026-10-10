import { ApiError } from './ApiError';
import type { TokenPair } from './types';

export const NETWORK_ERROR = 'NETWORK_ERROR';

export interface TokenStore {
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  setTokens(tokens: TokenPair): void | Promise<void>;
  clear(): void | Promise<void>;
}

export interface ApiClientOptions {
  baseUrl: string;
  tokens: TokenStore;
  fetchFn?: typeof fetch;
}

export interface RequestOptions {
  body?: unknown;
  headers?: Record<string, string>;
  /** Skip the Authorization header (auth endpoints). */
  anonymous?: boolean;
}

export type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

/**
 * Thin fetch wrapper: JSON in/out, bearer token, transparent refresh on 401 (one refresh shared by concurrent
 * requests) and typed {@link ApiError}s.
 */
export function createApiClient({ baseUrl, tokens, fetchFn = fetch }: ApiClientOptions) {
  let refreshing: Promise<boolean> | null = null;

  async function send(method: HttpMethod, path: string, options: RequestOptions): Promise<Response> {
    const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    const accessToken = tokens.getAccessToken();
    if (!options.anonymous && accessToken) {
      headers.Authorization = `Bearer ${accessToken}`;
    }
    try {
      return await fetchFn(`${baseUrl}${path}`, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      });
    } catch (cause) {
      // fetch only rejects when no HTTP response arrived: wrong URL, server down, firewall, no network
      if (__DEV__) {
        console.warn(`[api] ${method} ${baseUrl}${path} failed`, cause);
      }
      throw new ApiError(0, { code: NETWORK_ERROR, message: `Cannot reach server at ${baseUrl}` });
    }
  }

  async function refreshTokens(): Promise<boolean> {
    const refreshToken = tokens.getRefreshToken();
    if (!refreshToken) {
      return false;
    }
    const response = await send('POST', '/api/v1/auth/refresh', { body: { refreshToken }, anonymous: true });
    if (!response.ok) {
      await tokens.clear();
      return false;
    }
    await tokens.setTokens((await response.json()) as TokenPair);
    return true;
  }

  async function request<T>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
    let response = await send(method, path, options);
    if (response.status === 401 && !options.anonymous) {
      refreshing ??= refreshTokens().finally(() => {
        refreshing = null;
      });
      if (await refreshing) {
        response = await send(method, path, options);
      }
    }
    if (!response.ok) {
      let body: unknown;
      try {
        body = await response.json();
      } catch {
        body = undefined;
      }
      throw new ApiError(response.status, (body ?? {}) as object);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  return {
    request,
    get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, options),
    post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
      request<T>('POST', path, { ...options, body }),
    patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
      request<T>('PATCH', path, { ...options, body }),
    delete: <T = void>(path: string, options?: RequestOptions) => request<T>('DELETE', path, options),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
