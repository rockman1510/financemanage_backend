import { useTranslation } from 'react-i18next';
import { View } from 'react-native';
import type { Deposit } from '@/shared/api/types';
import { daysBetween, formatDate, formatPercent, todayIso } from '@/shared/lib/format';
import { Badge, type BadgeTone, Card, MoneyText, Text } from '@/shared/ui';

const statusTone: Record<Deposit['status'], BadgeTone> = { ACTIVE: 'positive', MATURED: 'warning', SETTLED: 'neutral' };

export function DepositCard({ deposit, onPress }: { deposit: Deposit; onPress: () => void }) {
  const { t } = useTranslation();
  const daysLeft = deposit.maturityDate && deposit.status === 'ACTIVE'
    ? daysBetween(todayIso(), deposit.maturityDate) : null;
  return (
    <Card onPress={onPress} accessibilityLabel={deposit.institutionName}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text variant="heading">{deposit.institutionName}</Text>
        <Badge label={t(`deposit.statuses.${deposit.status}`)} tone={statusTone[deposit.status]} />
      </View>
      <MoneyText amount={deposit.principal} currency={deposit.currency} variant="heading" />
      <Text tone="muted" variant="caption">
        {t(`deposit.types.${deposit.type}`)} · {formatPercent(deposit.annualRate)}
        {deposit.termMonths ? ` · ${deposit.termMonths}M` : ''}
        {deposit.maturityDate ? ` · ${formatDate(deposit.maturityDate)}` : ''}
      </Text>
      {daysLeft !== null && daysLeft >= 0 ? (
        <Text variant="caption" tone={daysLeft <= 7 ? 'warning' : 'muted'}>
          {t('deposit.daysLeft', { count: daysLeft })}
        </Text>
      ) : null}
    </Card>
  );
}
