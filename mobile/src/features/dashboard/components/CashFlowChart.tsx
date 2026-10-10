import { View } from 'react-native';
import type { MonthlyCashFlow } from '@/shared/api/types';
import { formatMoney } from '@/shared/lib/format';
import { Text, useTheme } from '@/shared/ui';

/** Lightweight bar chart built from Views – no native chart dependency. */
export function CashFlowChart({ data, currency }: { data: MonthlyCashFlow[]; currency: string }) {
  const { colors, spacing } = useTheme();
  const rows = data.filter((d) => d.currency === currency);
  const max = Math.max(1, ...rows.flatMap((d) => [d.inflow, d.outflow]));
  return (
    <View style={{ gap: spacing.sm }}>
      {rows.map((row) => (
        <View key={row.month} style={{ gap: 2 }} accessibilityLabel={`cashflow-${row.month}`}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <Text variant="caption" tone="muted">{row.month}</Text>
            <Text variant="caption" tone={row.net < 0 ? 'negative' : 'positive'}>{formatMoney(row.net, currency)}</Text>
          </View>
          <View style={{ height: 6, width: `${(row.inflow / max) * 100}%`, backgroundColor: colors.positive, borderRadius: 3 }} />
          <View style={{ height: 6, width: `${(row.outflow / max) * 100}%`, backgroundColor: colors.negative, borderRadius: 3 }} />
        </View>
      ))}
    </View>
  );
}
