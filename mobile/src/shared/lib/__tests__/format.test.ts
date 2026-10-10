import { daysBetween, formatDate, formatMoney, formatPercent, todayIso } from '../format';

describe('format', () => {
  it('formats VND without decimals and other currencies with two', () => {
    expect(formatMoney(100000000, 'VND')).toBe('100.000.000 ₫');
    expect(formatMoney(1234.5, 'USD')).toBe('1.234,50 USD');
    expect(formatMoney(null)).toBe('—');
  });

  it('formats percentages and dates', () => {
    expect(formatPercent(6.5)).toBe('6,5%');
    expect(formatDate('2025-07-01')).toBe('01/07/2025');
    expect(formatDate(undefined)).toBe('—');
  });

  it('computes local today and day differences', () => {
    expect(todayIso(new Date(2025, 0, 5))).toBe('2025-01-05');
    expect(daysBetween('2025-01-01', '2025-07-01')).toBe(181);
    expect(daysBetween('2025-03-10', '2025-03-01')).toBe(-9);
  });
});
