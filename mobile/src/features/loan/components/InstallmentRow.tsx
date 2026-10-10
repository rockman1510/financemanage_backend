import { useTranslation } from 'react-i18next';
import { View } from 'react-native';
import type { Installment, InstallmentStatus } from '@/shared/api/types';
import { formatDate, formatMoney } from '@/shared/lib/format';
import { Badge, type BadgeTone, Text } from '@/shared/ui';

const tone: Record<InstallmentStatus, BadgeTone> = {
  PENDING: 'neutral',
  PARTIAL: 'warning',
  PAID: 'positive',
  OVERDUE: 'negative',
};

export function InstallmentRow({ installment, currency }: { installment: Installment; currency: string }) {
  const { t } = useTranslation();
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 6 }}>
      <View style={{ gap: 2 }}>
        <Text>{t('loan.period', { no: installment.periodNo })} · {formatDate(installment.dueDate)}</Text>
        <Text variant="caption" tone="muted">
          {formatMoney(installment.principalDue, currency)} + {formatMoney(installment.interestDue, currency)}
        </Text>
      </View>
      <View style={{ alignItems: 'flex-end', gap: 2 }}>
        <Text variant="heading">{formatMoney(installment.totalDue, currency)}</Text>
        <Badge label={t(`loan.installmentStatuses.${installment.status}`)} tone={tone[installment.status]} />
      </View>
    </View>
  );
}
