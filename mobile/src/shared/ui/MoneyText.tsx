import { formatMoney } from '../lib/format';
import { Text } from './Text';

interface MoneyTextProps {
  amount: number | null | undefined;
  currency?: string;
  variant?: 'body' | 'heading' | 'amount' | 'caption';
  /** Colour positive amounts green and negative ones red. */
  signed?: boolean;
}

export function MoneyText({ amount, currency = 'VND', variant = 'body', signed = false }: MoneyTextProps) {
  const tone = signed && amount ? (amount < 0 ? 'negative' : 'positive') : 'default';
  return (
    <Text variant={variant} tone={tone}>
      {formatMoney(amount, currency)}
    </Text>
  );
}
