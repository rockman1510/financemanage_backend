import { defaultDepositForm, depositFormSchema, toCreateDepositRequest } from '../schemas';

const valid = { ...defaultDepositForm('2025-01-01'), institutionName: ' VCB ', principal: '100.000.000', annualRate: '6' };

describe('deposit form schema', () => {
  it('accepts a valid term deposit and maps it to the API request', () => {
    const values = depositFormSchema.parse(valid);

    expect(toCreateDepositRequest(values)).toEqual({
      institutionName: 'VCB',
      accountNumber: undefined,
      type: 'TERM',
      principal: 100000000,
      currency: 'VND',
      annualRate: 6,
      earlyWithdrawalRate: 0.1,
      termMonths: 6,
      openDate: '2025-01-01',
      interestPaymentMethod: 'AT_MATURITY',
      rolloverPolicy: 'NONE',
      calculationMethod: 'SIMPLE_ACTUAL_365',
      note: undefined,
    });
  });

  it('requires a term for term deposits only', () => {
    const term = depositFormSchema.safeParse({ ...valid, termMonths: '' });
    expect(term.success).toBe(false);
    expect(term.error?.issues[0]).toMatchObject({ path: ['termMonths'], message: 'deposit.termRequired' });

    const demand = depositFormSchema.parse({ ...valid, type: 'DEMAND', termMonths: '', rolloverPolicy: 'PRINCIPAL_ONLY' });
    const request = toCreateDepositRequest(demand);
    expect(request.termMonths).toBeUndefined();
    expect(request.rolloverPolicy).toBe('NONE');
  });

  it('rejects invalid amounts, rates and dates', () => {
    const result = depositFormSchema.safeParse({ ...valid, principal: '0', annualRate: '120', openDate: '01/01/2025' });
    const paths = result.error?.issues.map((i) => i.path[0]);
    expect(paths).toEqual(expect.arrayContaining(['principal', 'annualRate', 'openDate']));
  });
});
