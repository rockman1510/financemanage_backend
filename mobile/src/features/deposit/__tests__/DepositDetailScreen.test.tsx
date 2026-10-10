import { screen, userEvent, waitFor } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import { depositFixture } from '@/test/fixtures';
import { renderWithQuery } from '@/test/render';
import { depositApi } from '../api';
import DepositDetailScreen from '../screens/DepositDetailScreen';

jest.mock('../api', () => ({
  depositApi: { get: jest.fn(), settlementQuote: jest.fn(), settle: jest.fn(), remove: jest.fn() },
}));
const api = depositApi as jest.Mocked<typeof depositApi>;

describe('DepositDetailScreen', () => {
  beforeEach(() => {
    (useLocalSearchParams as jest.Mock).mockReturnValue({ id: 'd1' });
    api.get.mockResolvedValue(depositFixture);
    api.settlementQuote.mockResolvedValue({ date: '2025-04-01', principal: 100_000_000, interest: 123_288,
      total: 100_123_288, early: true, interestLostByEarlyWithdrawal: 1_356_164, currency: 'VND' });
    api.settle.mockResolvedValue({} as never);
  });

  it('shows interest figures and the early-withdrawal warning', async () => {
    await renderWithQuery(<DepositDetailScreen />);

    expect(await screen.findByText('1.479.452 ₫')).toBeOnTheScreen();
    expect(screen.getByText('2.975.342 ₫')).toBeOnTheScreen();
    expect(await screen.findByText('Rút trước hạn sẽ mất 1.356.164 ₫ tiền lãi')).toBeOnTheScreen();
  });

  it('settles only after confirmation', async () => {
    const user = userEvent.setup();
    await renderWithQuery(<DepositDetailScreen />);

    await user.press(await screen.findByRole('button', { name: 'Tất toán' }));
    expect(api.settle).not.toHaveBeenCalled();
    await user.press(screen.getByRole('button', { name: 'Xác nhận' }));

    await waitFor(() => expect(api.settle).toHaveBeenCalledWith('d1'));
  });
});
