import { ApiError } from '../ApiError';
import { createApiClient, type TokenStore } from '../client';
import { queryString } from '../queryString';

function jsonResponse(status: number, body?: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  } as Response;
}

function tokenStore(access: string | null = 'access-1', refresh: string | null = 'refresh-1') {
  const state = { access, refresh };
  const store: TokenStore & { state: typeof state } = {
    state,
    getAccessToken: () => state.access,
    getRefreshToken: () => state.refresh,
    setTokens: jest.fn((t) => {
      state.access = t.accessToken;
      state.refresh = t.refreshToken;
    }),
    clear: jest.fn(() => {
      state.access = null;
      state.refresh = null;
    }),
  };
  return store;
}

describe('createApiClient', () => {
  it('sends JSON with bearer token and parses the response', async () => {
    const fetchFn = jest.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    const client = createApiClient({ baseUrl: 'http://api', tokens: tokenStore(), fetchFn });

    const result = await client.post<{ ok: boolean }>('/x', { a: 1 }, { headers: { 'Idempotency-Key': 'k' } });

    expect(result).toEqual({ ok: true });
    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe('http://api/x');
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"a":1}');
    expect(init.headers).toMatchObject({ Authorization: 'Bearer access-1', 'Content-Type': 'application/json',
      'Idempotency-Key': 'k' });
  });

  it('omits the token for anonymous calls and handles 204', async () => {
    const fetchFn = jest.fn().mockResolvedValue(jsonResponse(204));
    const client = createApiClient({ baseUrl: '', tokens: tokenStore(), fetchFn });

    await expect(client.post('/auth', {}, { anonymous: true })).resolves.toBeUndefined();
    expect(fetchFn.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('refreshes once on 401 and retries the request', async () => {
    const tokens = tokenStore();
    const fetchFn = jest.fn()
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'access-2', refreshToken: 'refresh-2', expiresInSeconds: 900 }))
      .mockResolvedValueOnce(jsonResponse(200, [1, 2]));
    const client = createApiClient({ baseUrl: '', tokens, fetchFn });

    await expect(client.get('/deposits')).resolves.toEqual([1, 2]);
    expect(fetchFn.mock.calls[1][0]).toBe('/api/v1/auth/refresh');
    expect(JSON.parse(fetchFn.mock.calls[1][1].body)).toEqual({ refreshToken: 'refresh-1' });
    expect(fetchFn.mock.calls[2][1].headers.Authorization).toBe('Bearer access-2');
    expect(tokens.setTokens).toHaveBeenCalledTimes(1);
  });

  it('shares one refresh between concurrent requests', async () => {
    const tokens = tokenStore();
    let refreshCalls = 0;
    const fetchFn = jest.fn(async (url: string, init: RequestInit) => {
      if (url === '/api/v1/auth/refresh') {
        refreshCalls++;
        return jsonResponse(200, { accessToken: 'new', refreshToken: 'r2', expiresInSeconds: 1 });
      }
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === 'Bearer new' ? jsonResponse(200, url) : jsonResponse(401);
    });
    const client = createApiClient({ baseUrl: '', tokens, fetchFn: fetchFn as unknown as typeof fetch });

    const results = await Promise.all([client.get('/a'), client.get('/b'), client.get('/c')]);

    expect(results).toEqual(['/a', '/b', '/c']);
    expect(refreshCalls).toBe(1);
  });

  it('clears the session when refresh fails', async () => {
    const tokens = tokenStore();
    const fetchFn = jest.fn()
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'INVALID_CREDENTIALS' }));
    const client = createApiClient({ baseUrl: '', tokens, fetchFn });

    await expect(client.get('/x')).rejects.toMatchObject({ status: 401 });
    expect(tokens.clear).toHaveBeenCalled();
  });

  it('maps error bodies to ApiError', async () => {
    const fetchFn = jest.fn().mockResolvedValue(jsonResponse(400, {
      code: 'VALIDATION_FAILED', message: 'Request validation failed', fieldErrors: { principal: 'must be positive' },
    }));
    const client = createApiClient({ baseUrl: '', tokens: tokenStore(), fetchFn });

    const error = (await client.post('/deposits', {}).catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.fieldErrors.principal).toBe('must be positive');
  });
});

describe('network failures', () => {
  it('turns a rejected fetch into a NETWORK_ERROR ApiError', async () => {
    const fetchFn = jest.fn().mockRejectedValue(new TypeError('Network request failed'));
    const client = createApiClient({ baseUrl: 'http://192.168.1.15:8080', tokens: tokenStore(), fetchFn });

    const error = (await client.get('/x').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
  });
});

describe('queryString', () => {
  it('skips empty values and encodes', () => {
    expect(queryString({ status: 'ACTIVE', direction: undefined, q: 'a b' })).toBe('?status=ACTIVE&q=a%20b');
    expect(queryString({ a: undefined })).toBe('');
  });
});
