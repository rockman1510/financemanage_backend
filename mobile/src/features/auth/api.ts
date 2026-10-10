import { http } from '@/shared/api/http';
import type { Profile, TokenPair } from '@/shared/api/types';

export const authApi = {
  login: (email: string, password: string) =>
    http.post<TokenPair>('/api/v1/auth/login', { email, password }, { anonymous: true }),
  register: (email: string, password: string, fullName: string) =>
    http.post<TokenPair>('/api/v1/auth/register', { email, password, fullName }, { anonymous: true }),
  logout: (refreshToken: string) => http.post<void>('/api/v1/auth/logout', { refreshToken }, { anonymous: true }),
  me: () => http.get<Profile>('/api/v1/me'),
};
