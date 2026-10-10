import { defaultLoanForm, loanFormSchema, paymentSchema, toCreateLoanRequest } from '../schemas';

const valid = { ...defaultLoanForm('2025-01-15'), counterpartyName: 'VPBank', principal: '120000000', annualRate: '12' };

describe('loan form schema', () => {
  it('maps a valid loan to the API request', () => {
    expect(toCreateLoanRequest(loanFormSchema.parse(valid))).toMatchObject({
      counterpartyName: 'VPBank',
      direction: 'BORROW',
      principal: 120000000,
      annualRate: 12,
      termMonths: 12,
      repaymentMethod: 'EQUAL_PRINCIPAL',
      earlyRepaymentFeeRate: undefined,
    });
  });

  it('requires a rate unless the loan is interest free', () => {
    expect(loanFormSchema.safeParse({ ...valid, annualRate: '' }).success).toBe(false);

    const free = loanFormSchema.parse({ ...valid, annualRate: '', repaymentMethod: 'INTEREST_FREE' });
    expect(toCreateLoanRequest(free).annualRate).toBe(0);
  });

  it('validates term bounds', () => {
    expect(loanFormSchema.safeParse({ ...valid, termMonths: '0' }).success).toBe(false);
    expect(loanFormSchema.safeParse({ ...valid, termMonths: '601' }).success).toBe(false);
  });

  it('validates payments', () => {
    expect(paymentSchema.safeParse({ amount: '4.120.000', paidOn: '2025-02-15' }).success).toBe(true);
    expect(paymentSchema.safeParse({ amount: '0', paidOn: '2025-02-15' }).success).toBe(false);
  });
});
