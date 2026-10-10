import { render, screen, userEvent, waitFor } from '@testing-library/react-native';
import { ApiError } from '@/shared/api/ApiError';
import { useSession } from '@/shared/session/sessionStore';
import { authApi } from '../api';
import LoginScreen from '../screens/LoginScreen';

jest.mock('../api', () => ({ authApi: { login: jest.fn() } }));
const login = authApi.login as jest.Mock;

describe('LoginScreen', () => {
  beforeEach(async () => {
    login.mockReset();
    await useSession.getState().signOut();
  });

  it('validates input before calling the API', async () => {
    const user = userEvent.setup();
    await render(<LoginScreen />);

    await user.type(screen.getByLabelText('Email'), 'not-an-email');
    await user.press(screen.getByRole('button', { name: 'Đăng nhập' }));

    expect(await screen.findByText('Email không hợp lệ')).toBeOnTheScreen();
    expect(screen.getByText('Bắt buộc')).toBeOnTheScreen();
    expect(login).not.toHaveBeenCalled();
  });

  it('stores tokens after a successful login', async () => {
    login.mockResolvedValue({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 });
    const user = userEvent.setup();
    await render(<LoginScreen />);

    await user.type(screen.getByLabelText('Email'), 'an@example.com');
    await user.type(screen.getByLabelText('Mật khẩu'), 'secret123');
    await user.press(screen.getByRole('button', { name: 'Đăng nhập' }));

    await waitFor(() => expect(useSession.getState().status).toBe('signedIn'));
    expect(login).toHaveBeenCalledWith('an@example.com', 'secret123');
  });

  it('explains when the server cannot be reached', async () => {
    login.mockRejectedValue(new ApiError(0, { code: 'NETWORK_ERROR', message: 'x' }));
    const user = userEvent.setup();
    await render(<LoginScreen />);

    await user.type(screen.getByLabelText('Email'), 'an@example.com');
    await user.type(screen.getByLabelText('Mật khẩu'), 'secret123');
    await user.press(screen.getByRole('button', { name: 'Đăng nhập' }));

    expect(await screen.findByText(/Không kết nối được máy chủ http:\/\/localhost:8080/)).toBeOnTheScreen();
  });

  it('shows a friendly message for wrong credentials', async () => {
    login.mockRejectedValue(new ApiError(401, { code: 'INVALID_CREDENTIALS', message: 'x' }));
    const user = userEvent.setup();
    await render(<LoginScreen />);

    await user.type(screen.getByLabelText('Email'), 'an@example.com');
    await user.type(screen.getByLabelText('Mật khẩu'), 'wrong');
    await user.press(screen.getByRole('button', { name: 'Đăng nhập' }));

    expect(await screen.findByText('Email hoặc mật khẩu không đúng')).toBeOnTheScreen();
  });
});
