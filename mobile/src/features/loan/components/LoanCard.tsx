import { useTranslation } from 'react-i18next';
import { View } from 'react-native';
import type { LoanSummary } from '@/shared/api/types';
import { formatDate, formatMoney, formatPercent } from '@/shared/lib/format';
import { Badge, Card, MoneyText, Text } from '@/shared/ui';

export function LoanCard({ loan, onPress }: { loan: LoanSummary; onPress: () => void }) {
  const { t } = useTranslation();
  const borrowed = loan.direction === 'BORROW';
  return (
    <Card onPress={onPress} accessibilityLabel={loan.counterpartyName}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text variant="heading">{loan.counterpartyName}</Text>
        <Badge label={t(`loan.directions.${loan.direction}`)} tone={borrowed ? 'negative' : 'positive'} />
      </View>
      <Text variant="caption" tone="muted">{t('loan.outstanding')}</Text>
      <MoneyText amount={loan.outstandingPrincipal} currency={loan.currency} variant="heading" />
      <Text variant="caption" tone="muted">
        {t(`loan.methods.${loan.repaymentMethod}`)} · {formatPercent(loan.currentRate)} · {loan.termMonths}M
      </Text>
      {loan.status === 'PAID_OFF' ? <Badge label={t('loan.paidOff')} /> : null}
      {loan.nextDueDate ? (
        <Text variant="caption">
          {t('loan.nextDue')}: {formatDate(loan.nextDueDate)} · {formatMoney(loan.nextDueAmount, loan.currency)}
        </Text>
      ) : null}
      {loan.overdueInstallments > 0 ? (
        <Badge label={t('loan.overdue', { count: loan.overdueInstallments })} tone="negative" />
      ) : null}
    </Card>
  );
}
