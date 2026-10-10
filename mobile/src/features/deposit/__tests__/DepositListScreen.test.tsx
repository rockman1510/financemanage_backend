import { screen, userEvent } from '@testing-library/react-native';
import { depositFixture } from '@/test/fixtures';
import { renderWithQuery } from '@/test/render';
import { depositApi } from '../api';
import DepositListScreen from '../screens/DepositListScreen';
import { mockRouter } from '@/test/router';

jest.mock('../api', () => ({ depositApi: { list: jest.fn() } }));
const list = depositApi.list as jest.Mock;

describe('DepositListScreen', () => {
  beforeEach(() => list.mockReset());

  it('lists active deposits and opens the detail screen', async () => {
    list.mockResolvedValue([depositFixture]);
    const user = userEvent.setup();
    await renderWithQuery(<DepositListScreen />);

    expect(await screen.findByText('Vietcombank')).toBeOnTheScreen();
    expect(screen.getByText('100.000.000 ₫')).toBeOnTheScreen();
    expect(list).toHaveBeenCalledWith('ACTIVE');

    await user.press(screen.getByRole('button', { name: 'Vietcombank' }));
    expect(mockRouter.push).toHaveBeenCalledWith('/deposits/d1');
  });

  it('changes the filter and shows the empty state', async () => {
    list.mockResolvedValue([]);
    const user = userEvent.setup();
    await renderWithQuery(<DepositListScreen />);

    expect(await screen.findByText('Chưa có sổ tiết kiệm nào')).toBeOnTheScreen();
    await user.press(screen.getByRole('radio', { name: 'Đã tất toán' }));
    expect(list).toHaveBeenLastCalledWith('SETTLED');
  });
});
