import { useRouter } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { View } from 'react-native';
import { formatDate, formatMoney } from '@/shared/lib/format';
import { Badge, Card, EmptyState, ErrorView, LoadingView, MoneyText, Row, Screen, Text } from '@/shared/ui';
import { CashFlowChart } from '../components/CashFlowChart';
import { useDashboard } from '../hooks';

export default function DashboardScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const query = useDashboard();

  if (query.isPending) return <LoadingView />;
  if (query.isError) return <ErrorView error={query.error} onRetry={() => query.refetch()} />;

  const dashboard = query.data;
  const isEmpty = dashboard.activeDeposits === 0 && dashboard.activeLoans === 0;

  return (
    <Screen refreshing={query.isRefetching} onRefresh={() => query.refetch()}>
      {isEmpty ? <EmptyState title={t('dashboard.empty')} /> : null}
      {dashboard.totals.map((total) => (
        <Card key={total.currency}>
          <Text tone="muted">{t('dashboard.netWorth')} ({total.currency})</Text>
          <MoneyText amount={total.netWorth} currency={total.currency} variant="amount" />
          <Row label={t('dashboard.deposits')}><MoneyText amount={total.deposits} currency={total.currency} /></Row>
          <Row label={t('dashboard.accruedInterest')}>
            <MoneyText amount={total.accruedInterest} currency={total.currency} signed />
          </Row>
          <Row label={t('dashboard.lent')}><MoneyText amount={total.lentOutstanding} currency={total.currency} /></Row>
          <Row label={t('dashboard.borrowed')}>
            <MoneyText amount={-total.borrowedOutstanding} currency={total.currency} signed />
          </Row>
        </Card>
      ))}

      <View style={{ flexDirection: 'row', gap: 12 }}>
        <Card style={{ flex: 1 }} onPress={() => router.push('/deposits')}>
          <Text tone="muted" variant="caption">{t('dashboard.activeDeposits')}</Text>
          <Text variant="title">{dashboard.activeDeposits}</Text>
        </Card>
        <Card style={{ flex: 1 }} onPress={() => router.push('/loans')}>
          <Text tone="muted" variant="caption">{t('dashboard.activeLoans')}</Text>
          <Text variant="title">{dashboard.activeLoans}</Text>
          {dashboard.overdueInstallments > 0 ? (
            <Badge label={t('dashboard.overdue', { count: dashboard.overdueInstallments })} tone="negative" />
          ) : null}
        </Card>
      </View>

      <Card>
        <Text variant="heading">{t('dashboard.upcoming')}</Text>
        {dashboard.upcoming.length === 0 ? <Text tone="muted">{t('dashboard.noUpcoming')}</Text> : null}
        {dashboard.upcoming.map((item) => (
          <Row key={`${item.sourceId}-${item.date}`} label={`${formatDate(item.date)} · ${item.title}`}>
            <Text tone={item.direction === 'IN' ? 'positive' : 'negative'}>
              {item.direction === 'IN' ? '+' : '−'}{formatMoney(item.amount, item.currency)}
            </Text>
          </Row>
        ))}
      </Card>

      {dashboard.monthlyCashFlow.length ? (
        <Card>
          <Text variant="heading">{t('dashboard.cashFlow')}</Text>
          <CashFlowChart data={dashboard.monthlyCashFlow} currency={dashboard.totals[0]?.currency ?? 'VND'} />
        </Card>
      ) : null}
    </Screen>
  );
}
