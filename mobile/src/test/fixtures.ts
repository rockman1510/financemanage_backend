import type { Dashboard, Deposit, LoanDetail, LoanSummary } from '@/shared/api/types';

export const depositFixture: Deposit = {
  id: 'd1',
  institutionName: 'Vietcombank',
  accountNumber: '123',
  type: 'TERM',
  principal: 100_000_000,
  currency: 'VND',
  annualRate: 6,
  earlyWithdrawalRate: 0.5,
  termMonths: 6,
  openDate: '2025-01-01',
  maturityDate: '2025-07-01',
  interestPaymentMethod: 'AT_MATURITY',
  rolloverPolicy: 'NONE',
  calculationMethod: 'SIMPLE_ACTUAL_365',
  status: 'ACTIVE',
  note: null,
  settledOn: null,
  settledAmount: null,
  accruedInterest: 1_479_452,
  expectedInterestAtMaturity: 2_975_342,
  transactions: [{ id: 't1', type: 'OPEN', amount: 100_000_000, occurredOn: '2025-01-01' }],
};

export const loanSummaryFixture: LoanSummary = {
  id: 'l1',
  counterpartyName: 'VPBank',
  direction: 'BORROW',
  type: 'UNSECURED',
  principal: 12_000_000,
  currency: 'VND',
  currentRate: 12,
  termMonths: 3,
  startDate: '2025-01-15',
  repaymentMethod: 'EQUAL_PRINCIPAL',
  status: 'ACTIVE',
  outstandingPrincipal: 12_000_000,
  nextDueDate: '2025-02-15',
  nextDueAmount: 4_120_000,
  overdueInstallments: 1,
  note: null,
};

export const loanDetailFixture: LoanDetail = {
  summary: loanSummaryFixture,
  earlyRepaymentFeeRate: 1,
  totalScheduledInterest: 240_000,
  ratePeriods: [{ effectiveFrom: '2025-01-15', annualRate: 12 }],
  installments: [
    { id: 'i1', periodNo: 1, dueDate: '2025-02-15', principalDue: 4_000_000, interestDue: 120_000,
      totalDue: 4_120_000, principalPaid: 0, interestPaid: 0, remaining: 4_120_000, status: 'OVERDUE' },
    { id: 'i2', periodNo: 2, dueDate: '2025-03-15', principalDue: 4_000_000, interestDue: 80_000,
      totalDue: 4_080_000, principalPaid: 0, interestPaid: 0, remaining: 4_080_000, status: 'PENDING' },
  ],
  repayments: [],
};

export const dashboardFixture: Dashboard = {
  asOf: '2025-03-10',
  totals: [{ currency: 'VND', deposits: 100_000_000, accruedInterest: 1_000_000, lentOutstanding: 5_000_000,
    borrowedOutstanding: 30_000_000, netWorth: 76_000_000 }],
  activeDeposits: 2,
  activeLoans: 1,
  overdueInstallments: 1,
  upcoming: [{ date: '2025-03-15', amount: 4_080_000, currency: 'VND', direction: 'OUT', sourceType: 'LOAN',
    sourceId: 'l1', title: 'Trả nợ VPBank – kỳ 2' }],
  monthlyCashFlow: [{ month: '2025-03', currency: 'VND', inflow: 0, outflow: 4_080_000, net: -4_080_000 }],
};
