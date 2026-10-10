/**
 * Parses user-entered amounts: "100000000", "100.000.000", "100,000,000", "1.234,5" or "1234.5".
 * Returns NaN for anything that is not a number.
 */
export function parseAmount(input: string): number {
  const value = input.replace(/[\s₫]/g, '');
  if (value === '') {
    return Number.NaN;
  }
  if (/^\d{1,3}([.,]\d{3})+$/.test(value) && !/^\d{1,3}[.,]\d{3}$/.test(value)) {
    return Number(value.replace(/[.,]/g, '')); // grouped thousands, e.g. 100.000.000
  }
  if (/^\d{1,3}(\.\d{3})+,\d+$/.test(value)) {
    return Number(value.replace(/\./g, '').replace(',', '.')); // 1.234,5
  }
  if (/^\d{1,3}(,\d{3})+\.\d+$/.test(value)) {
    return Number(value.replace(/,/g, '')); // 1,234.5
  }
  if (/^\d{1,3}\.\d{3}$/.test(value)) {
    return Number(value.replace('.', '')); // 100.000 (vi-VN thousands)
  }
  if (/^\d+(,\d+)?$/.test(value)) {
    return Number(value.replace(',', '.'));
  }
  if (/^\d+(\.\d+)?$/.test(value)) {
    return Number(value);
  }
  return Number.NaN;
}

export const ISO_DATE = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;

/** Collision-resistant key for idempotent POSTs (one per form submission attempt). */
export function newIdempotencyKey(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
