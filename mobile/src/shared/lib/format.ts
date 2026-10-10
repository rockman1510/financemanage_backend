/** Locale-aware formatting helpers. Amounts arrive from the API already rounded to the currency unit. */

const DEFAULT_LOCALE = 'vi-VN';

export function formatMoney(amount: number | null | undefined, currency = 'VND', locale = DEFAULT_LOCALE): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '—';
  }
  const digits = currency === 'VND' ? 0 : 2;
  const number = new Intl.NumberFormat(locale, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(amount);
  return `${number} ${currency === 'VND' ? '₫' : currency}`;
}

export function formatPercent(value: number | null | undefined, locale = DEFAULT_LOCALE): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(value)}%`;
}

/** yyyy-MM-dd -> dd/MM/yyyy */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [year, month, day] = iso.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

/** Local date as yyyy-MM-dd. */
export function todayIso(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function daysBetween(fromIso: string, toIso: string): number {
  const from = Date.UTC(+fromIso.slice(0, 4), +fromIso.slice(5, 7) - 1, +fromIso.slice(8, 10));
  const to = Date.UTC(+toIso.slice(0, 4), +toIso.slice(5, 7) - 1, +toIso.slice(8, 10));
  return Math.round((to - from) / 86_400_000);
}
