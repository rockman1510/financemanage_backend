/**
 * Feature flags – toggle capabilities per build without code changes (EXPO_PUBLIC_FEATURE_*).
 * Later these can come from a remote-config endpoint.
 */
const flag = (value: string | undefined, fallback: boolean) =>
  value === undefined ? fallback : value === 'true' || value === '1';

export const features = {
  loanPrepayment: flag(process.env.EXPO_PUBLIC_FEATURE_LOAN_PREPAYMENT, true),
  loanRateChange: flag(process.env.EXPO_PUBLIC_FEATURE_LOAN_RATE_CHANGE, true),
};

export type FeatureName = keyof typeof features;
