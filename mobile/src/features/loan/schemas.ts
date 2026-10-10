import { z } from 'zod';
import type { CreateLoanRequest } from '@/shared/api/types';
import { ISO_DATE, parseAmount } from '@/shared/lib/parse';

const percent = (v: string) => {
  const n = parseAmount(v);
  return n >= 0 && n <= 100;
};

export const loanFormSchema = z
  .object({
    counterpartyName: z.string().trim().min(1, 'common.required').max(120),
    direction: z.enum(['BORROW', 'LEND']),
    type: z.enum(['MORTGAGE', 'UNSECURED', 'INSTALLMENT', 'PERSONAL']),
    principal: z.string().refine((v) => parseAmount(v) > 0, 'common.invalidNumber'),
    currency: z.string().regex(/^[A-Z]{3}$/, 'common.required'),
    annualRate: z.string(),
    termMonths: z.string().refine((v) => /^\d+$/.test(v) && +v >= 1 && +v <= 600, 'common.invalidNumber'),
    startDate: z.string().regex(ISO_DATE, 'common.invalidDate'),
    repaymentMethod: z.enum(['EQUAL_PRINCIPAL', 'ANNUITY', 'BULLET', 'INTEREST_FREE']),
    earlyRepaymentFeeRate: z.string().refine((v) => v.trim() === '' || percent(v), 'common.invalidNumber'),
    note: z.string().max(500),
  })
  .superRefine((values, ctx) => {
    if (values.repaymentMethod !== 'INTEREST_FREE' && !percent(values.annualRate)) {
      ctx.addIssue({ code: 'custom', path: ['annualRate'], message: 'common.invalidNumber' });
    }
  });

export type LoanFormValues = z.infer<typeof loanFormSchema>;

export function defaultLoanForm(today: string): LoanFormValues {
  return {
    counterpartyName: '',
    direction: 'BORROW',
    type: 'UNSECURED',
    principal: '',
    currency: 'VND',
    annualRate: '',
    termMonths: '12',
    startDate: today,
    repaymentMethod: 'EQUAL_PRINCIPAL',
    earlyRepaymentFeeRate: '',
    note: '',
  };
}

export function toCreateLoanRequest(values: LoanFormValues): CreateLoanRequest {
  const interestFree = values.repaymentMethod === 'INTEREST_FREE';
  return {
    counterpartyName: values.counterpartyName.trim(),
    direction: values.direction,
    type: values.type,
    principal: parseAmount(values.principal),
    currency: values.currency,
    annualRate: interestFree ? 0 : parseAmount(values.annualRate),
    termMonths: Number(values.termMonths),
    startDate: values.startDate,
    repaymentMethod: values.repaymentMethod,
    earlyRepaymentFeeRate: values.earlyRepaymentFeeRate.trim() ? parseAmount(values.earlyRepaymentFeeRate) : undefined,
    note: values.note.trim() || undefined,
  };
}

/** Payment / prepayment form (amount + date). */
export const paymentSchema = z.object({
  amount: z.string().refine((v) => parseAmount(v) > 0, 'common.invalidNumber'),
  paidOn: z.string().regex(ISO_DATE, 'common.invalidDate'),
});

export type PaymentValues = z.infer<typeof paymentSchema>;

export const rateChangeSchema = z.object({
  annualRate: z.string().refine(percent, 'common.invalidNumber'),
  effectiveFrom: z.string().regex(ISO_DATE, 'common.invalidDate'),
});

export type RateChangeValues = z.infer<typeof rateChangeSchema>;
