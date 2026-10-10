import { create } from 'zustand';
import type { TokenPair } from '../api/types';
import { secureStorage } from '../storage/secureStorage';

const ACCESS_KEY = 'fm.accessToken';
const REFRESH_KEY = 'fm.refreshToken';

type Status = 'loading' | 'signedIn' | 'signedOut';

interface SessionState {
  status: Status;
  accessToken: string | null;
  refreshToken: string | null;
  /** Loads persisted tokens on app start. */
  hydrate: () => Promise<void>;
  setTokens: (tokens: TokenPair) => Promise<void>;
  signOut: () => Promise<void>;
}

export const useSession = create<SessionState>((set) => ({
  status: 'loading',
  accessToken: null,
  refreshToken: null,
  hydrate: async () => {
    const [accessToken, refreshToken] = await Promise.all([
      secureStorage.getItem(ACCESS_KEY),
      secureStorage.getItem(REFRESH_KEY),
    ]);
    set({ accessToken, refreshToken, status: refreshToken ? 'signedIn' : 'signedOut' });
  },
  setTokens: async ({ accessToken, refreshToken }) => {
    set({ accessToken, refreshToken, status: 'signedIn' });
    await Promise.all([secureStorage.setItem(ACCESS_KEY, accessToken), secureStorage.setItem(REFRESH_KEY, refreshToken)]);
  },
  signOut: async () => {
    set({ accessToken: null, refreshToken: null, status: 'signedOut' });
    await Promise.all([secureStorage.removeItem(ACCESS_KEY), secureStorage.removeItem(REFRESH_KEY)]);
  },
}));
