/** API contracts – mirror the backend DTOs (`/api/v1`). */

export type Uuid = string;
export type IsoDate = string; // yyyy-MM-dd

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

export interface Profile {
  id: Uuid;
  email: string;
  fullName: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

// ---- Deposits ----
export type DepositType = 'DEMAND' | 'TERM';
export type DepositStatus = 'ACTIVE' | 'MATURED' | 'SETTLED';
export type InterestPaymentMethod = 'AT_MATURITY' | 'MONTHLY' | 'UPFRONT';
export type RolloverPolicy = 'NONE' | 'PRINCIPAL_ONLY' | 'PRINCIPAL_AND_INTEREST';
export type InterestCalculationMethod = 'SIMPLE_ACTUAL_365' | 'SIMPLE_30_360' | 'COMPOUND_MONTHLY';

export interface CreateDepositRequest {
  institutionName: string;
  accountNumber?: string;
  type: DepositType;
  principal: number;
  currency: string;
  annualRate: number;
  earlyWithdrawalRate?: number;
  termMonths?: number;
  openDate: IsoDate;
  interestPaymentMethod?: InterestPaymentMethod;
  rolloverPolicy?: RolloverPolicy;
  calculationMethod?: InterestCalculationMethod;
  note?: string;
}

export interface DepositTransaction {
  id: Uuid;
  type: 'OPEN' | 'ROLLOVER' | 'INTEREST_PAID' | 'SETTLE';
  amount: number;
  occurredOn: IsoDate;
}

export interface Deposit {
  id: Uuid;
  institutionName: string;
  accountNumber: string | null;
  type: DepositType;
  principal: number;
  currency: string;
  annualRate: number;
  earlyWithdrawalRate: number;
  termMonths: number | null;
  openDate: IsoDate;
  maturityDate: IsoDate | null;
  interestPaymentMethod: InterestPaymentMethod;
  rolloverPolicy: RolloverPolicy;
  calculationMethod: InterestCalculationMethod;
  status: DepositStatus;
  note: string | null;
  settledOn: IsoDate | null;
  settledAmount: number | null;
  accruedInterest: number;
  expectedInterestAtMaturity: number | null;
  transactions: DepositTransaction[];
}

export interface SettlementQuote {
  date: IsoDate;
  principal: number;
  interest: number;
  total: number;
  early: boolean;
  interestLostByEarlyWithdrawal: number;
  currency: string;
}

export interface DepositPreview {
  maturityDate: IsoDate | null;
  expectedInterest: number;
  totalAtMaturity: number;
  currency: string;
}

// ---- Loans ----
export type LoanDirection = 'BORROW' | 'LEND';
export type LoanType = 'MORTGAGE' | 'UNSECURED' | 'INSTALLMENT' | 'PERSONAL';
export type LoanStatus = 'ACTIVE' | 'PAID_OFF';
export type RepaymentMethod = 'EQUAL_PRINCIPAL' | 'ANNUITY' | 'BULLET' | 'INTEREST_FREE';
export type InstallmentStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERDUE';

export interface CreateLoanRequest {
  counterpartyName: string;
  direction: LoanDirection;
  type?: LoanType;
  principal: number;
  currency: string;
  annualRate?: number;
  termMonths: number;
  startDate: IsoDate;
  repaymentMethod: RepaymentMethod;
  earlyRepaymentFeeRate?: number;
  note?: string;
}

export interface LoanSummary {
  id: Uuid;
  counterpartyName: string;
  direction: LoanDirection;
  type: LoanType;
  principal: number;
  currency: string;
  currentRate: number;
  termMonths: number;
  startDate: IsoDate;
  repaymentMethod: RepaymentMethod;
  status: LoanStatus;
  outstandingPrincipal: number;
  nextDueDate: IsoDate | null;
  nextDueAmount: number | null;
  overdueInstallments: number;
  note: string | null;
}

export interface Installment {
  id: Uuid;
  periodNo: number;
  dueDate: IsoDate;
  principalDue: number;
  interestDue: number;
  totalDue: number;
  principalPaid: number;
  interestPaid: number;
  remaining: number;
  status: InstallmentStatus;
}

export interface Repayment {
  id: Uuid;
  type: 'REGULAR' | 'PREPAYMENT';
  paidOn: IsoDate;
  amount: number;
  principalPortion: number;
  interestPortion: number;
  feePortion: number;
}

export interface LoanDetail {
  summary: LoanSummary;
  earlyRepaymentFeeRate: number;
  totalScheduledInterest: number;
  ratePeriods: { effectiveFrom: IsoDate; annualRate: number }[];
  installments: Installment[];
  repayments: Repayment[];
}

export interface SchedulePreview {
  installments: Installment[];
  totalInterest: number;
  totalPayment: number;
  currency: string;
}

// ---- Dashboard & notifications ----
export interface UpcomingItem {
  date: IsoDate;
  amount: number;
  currency: string;
  direction: 'IN' | 'OUT';
  sourceType: 'DEPOSIT' | 'LOAN';
  sourceId: Uuid;
  title: string;
}

export interface CurrencyTotals {
  currency: string;
  deposits: number;
  accruedInterest: number;
  lentOutstanding: number;
  borrowedOutstanding: number;
  netWorth: number;
}

export interface MonthlyCashFlow {
  month: string;
  currency: string;
  inflow: number;
  outflow: number;
  net: number;
}

export interface Dashboard {
  asOf: IsoDate;
  totals: CurrencyTotals[];
  activeDeposits: number;
  activeLoans: number;
  overdueInstallments: number;
  upcoming: UpcomingItem[];
  monthlyCashFlow: MonthlyCashFlow[];
}

export interface AppNotification {
  id: Uuid;
  type: string;
  title: string;
  body: string;
  referenceType: 'DEPOSIT' | 'LOAN' | null;
  referenceId: Uuid | null;
  createdAt: string;
  read: boolean;
}
