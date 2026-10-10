import { screen, userEvent, waitFor } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import { loanDetailFixture } from '@/test/fixtures';
import { renderWithQuery } from '@/test/render';
import { loanApi } from '../api';
import LoanDetailScreen from '../screens/LoanDetailScreen';

jest.mock('../api', () => ({
  loanApi: { get: jest.fn(), repay: jest.fn(), prepay: jest.fn(), changeRate: jest.fn(), remove: jest.fn() },
}));
const api = loanApi as jest.Mocked<typeof loanApi>;

describe('LoanDetailScreen', () => {
  beforeEach(() => {
    (useLocalSearchParams as jest.Mock).mockReturnValue({ id: 'l1' });
    api.get.mockResolvedValue(loanDetailFixture);
    api.repay.mockResolvedValue({} as never);
  });

  it('renders the repayment schedule with statuses', async () => {
    await renderWithQuery(<LoanDetailScreen />);

    expect(await screen.findByText('Kỳ 1 · 15/02/2025')).toBeOnTheScreen();
    expect(screen.getByText('Quá hạn')).toBeOnTheScreen();
    expect(screen.getByText('Chưa đến hạn')).toBeOnTheScreen();
  });

  it('records a repayment with the next due amount and a stable idempotency key', async () => {
    const user = userEvent.setup();
    await renderWithQuery(<LoanDetailScreen />);

    expect(await screen.findByDisplayValue('4120000')).toBeOnTheScreen();
    await user.press(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(api.repay).toHaveBeenCalledTimes(1));

    const [id, amount, , key] = api.repay.mock.calls[0];
    expect(id).toBe('l1');
    expect(amount).toBe(4120000);
    expect(key).toMatch(/.+-.+/);
  });

  it('offers prepayment and rate change when the feature flags are on', async () => {
    await renderWithQuery(<LoanDetailScreen />);

    expect(await screen.findByRole('radio', { name: 'Trả trước hạn' })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: 'Đổi lãi suất' })).toBeOnTheScreen();
  });
});
