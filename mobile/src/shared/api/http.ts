import { env } from '../config/env';
import { useSession } from '../session/sessionStore';
import { createApiClient } from './client';

/** Application-wide API client bound to the session store. */
export const http = createApiClient({
  baseUrl: env.apiUrl,
  tokens: {
    getAccessToken: () => useSession.getState().accessToken,
    getRefreshToken: () => useSession.getState().refreshToken,
    setTokens: (tokens) => useSession.getState().setTokens(tokens),
    clear: () => useSession.getState().signOut(),
  },
});
