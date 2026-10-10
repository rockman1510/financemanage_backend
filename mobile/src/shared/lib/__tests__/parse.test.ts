import { ISO_DATE, newIdempotencyKey, parseAmount } from '../parse';

describe('parseAmount', () => {
  it.each([
    ['100000000', 100000000],
    ['100.000.000', 100000000],
    ['100,000,000', 100000000],
    ['100.000', 100000],
    ['1.234,5', 1234.5],
    ['1,234.5', 1234.5],
    ['6.5', 6.5],
    ['6,5', 6.5],
    [' 5 000 000 ₫ ', 5000000],
  ])('parses %p', (input, expected) => {
    expect(parseAmount(input)).toBe(expected);
  });

  it.each(['', 'abc', '1.2.3,4,5', '-5'])('rejects %p', (input) => {
    expect(parseAmount(input)).toBeNaN();
  });
});

describe('helpers', () => {
  it('validates ISO dates', () => {
    expect(ISO_DATE.test('2025-02-28')).toBe(true);
    expect(ISO_DATE.test('2025-13-01')).toBe(false);
    expect(ISO_DATE.test('01/02/2025')).toBe(false);
  });

  it('generates distinct idempotency keys', () => {
    expect(newIdempotencyKey()).not.toBe(newIdempotencyKey());
  });
});
