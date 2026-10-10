import { screen } from '@testing-library/react-native';
import { dashboardFixture } from '@/test/fixtures';
import { renderWithQuery } from '@/test/render';
import { dashboardApi } from '../api';
import DashboardScreen from '../screens/DashboardScreen';

jest.mock('../api', () => ({ dashboardApi: { get: jest.fn() } }));

describe('DashboardScreen', () => {
  it('shows net worth, counters and upcoming cash flows', async () => {
    (dashboardApi.get as jest.Mock).mockResolvedValue(dashboardFixture);
    await renderWithQuery(<DashboardScreen />);

    expect(await screen.findByText('76.000.000 ₫')).toBeOnTheScreen();
    expect(screen.getByText('1 kỳ quá hạn')).toBeOnTheScreen();
    expect(screen.getByText('15/03/2025 · Trả nợ VPBank – kỳ 2')).toBeOnTheScreen();
    expect(screen.getByText('−4.080.000 ₫')).toBeOnTheScreen();
  });

  it('shows an error with retry', async () => {
    (dashboardApi.get as jest.Mock).mockRejectedValue(new Error('offline'));
    await renderWithQuery(<DashboardScreen />);

    expect(await screen.findByText('Đã có lỗi xảy ra, vui lòng thử lại.')).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeOnTheScreen();
  });
});
