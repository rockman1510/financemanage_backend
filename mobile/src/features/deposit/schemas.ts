import { z } from 'zod';
import type {
  CreateDepositRequest,
  DepositType,
  InterestCalculationMethod,
  InterestPaymentMethod,
  RolloverPolicy,
} from '@/shared/api/types';
import { ISO_DATE, parseAmount } from '@/shared/lib/parse';

const positiveAmount = z.string().refine((v) => parseAmount(v) > 0, 'common.invalidNumber');
const rate = z.string().refine((v) => {
  const n = parseAmount(v);
  return n >= 0 && n <= 100;
}, 'common.invalidNumber');
const optionalRate = z.string().refine((v) => v.trim() === '' || (parseAmount(v) >= 0 && parseAmount(v) <= 100),
  'common.invalidNumber');

/** Form values are strings (what the user typed); {@link toCreateDepositRequest} converts them. */
export const depositFormSchema = z
  .object({
    institutionName: z.string().trim().min(1, 'common.required').max(120),
    accountNumber: z.string().max(64),
    type: z.enum(['DEMAND', 'TERM']),
    principal: positiveAmount,
    currency: z.string().regex(/^[A-Z]{3}$/, 'common.required'),
    annualRate: rate,
    earlyWithdrawalRate: optionalRate,
    termMonths: z.string(),
    openDate: z.string().regex(ISO_DATE, 'common.invalidDate'),
    interestPaymentMethod: z.enum(['AT_MATURITY', 'MONTHLY', 'UPFRONT']),
    rolloverPolicy: z.enum(['NONE', 'PRINCIPAL_ONLY', 'PRINCIPAL_AND_INTEREST']),
    calculationMethod: z.enum(['SIMPLE_ACTUAL_365', 'SIMPLE_30_360', 'COMPOUND_MONTHLY']),
    note: z.string().max(500),
  })
  .superRefine((values, ctx) => {
    if (values.type === 'TERM') {
      const term = Number(values.termMonths);
      if (!Number.isInteger(term) || term < 1 || term > 600) {
        ctx.addIssue({ code: 'custom', path: ['termMonths'], message: 'deposit.termRequired' });
      }
    }
  });

export type DepositFormValues = z.infer<typeof depositFormSchema>;

export function defaultDepositForm(today: string): DepositFormValues {
  return {
    institutionName: '',
    accountNumber: '',
    type: 'TERM' as DepositType,
    principal: '',
    currency: 'VND',
    annualRate: '',
    earlyWithdrawalRate: '0.1',
    termMonths: '6',
    openDate: today,
    interestPaymentMethod: 'AT_MATURITY' as InterestPaymentMethod,
    rolloverPolicy: 'NONE' as RolloverPolicy,
    calculationMethod: 'SIMPLE_ACTUAL_365' as InterestCalculationMethod,
    note: '',
  };
}

export function toCreateDepositRequest(values: DepositFormValues): CreateDepositRequest {
  const term = values.type === 'TERM';
  return {
    institutionName: values.institutionName.trim(),
    accountNumber: values.accountNumber.trim() || undefined,
    type: values.type,
    principal: parseAmount(values.principal),
    currency: values.currency,
    annualRate: parseAmount(values.annualRate),
    earlyWithdrawalRate: values.earlyWithdrawalRate.trim() ? parseAmount(values.earlyWithdrawalRate) : undefined,
    termMonths: term ? Number(values.termMonths) : undefined,
    openDate: values.openDate,
    interestPaymentMethod: values.interestPaymentMethod,
    rolloverPolicy: term ? values.rolloverPolicy : 'NONE',
    calculationMethod: values.calculationMethod,
    note: values.note.trim() || undefined,
  };
}
