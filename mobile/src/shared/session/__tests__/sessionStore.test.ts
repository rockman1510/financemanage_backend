import { secureStorage } from '@/shared/storage/secureStorage';
import { useSession } from '../sessionStore';

const tokens = { accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 };

describe('sessionStore', () => {
  beforeEach(async () => {
    await useSession.getState().signOut();
  });

  it('persists tokens and signs in', async () => {
    await useSession.getState().setTokens(tokens);

    expect(useSession.getState().status).toBe('signedIn');
    expect(secureStorage.setItem).toHaveBeenCalledWith('fm.refreshToken', 'r');
  });

  it('hydrates from storage', async () => {
    await useSession.getState().setTokens(tokens);
    useSession.setState({ status: 'loading', accessToken: null, refreshToken: null });

    await useSession.getState().hydrate();

    expect(useSession.getState()).toMatchObject({ status: 'signedIn', accessToken: 'a', refreshToken: 'r' });
  });

  it('hydrates to signedOut without a refresh token', async () => {
    await useSession.getState().hydrate();

    expect(useSession.getState().status).toBe('signedOut');
  });
});
