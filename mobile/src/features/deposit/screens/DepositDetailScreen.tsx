import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { View } from 'react-native';
import { daysBetween, formatDate, formatMoney, formatPercent, todayIso } from '@/shared/lib/format';
import { Button, Card, ErrorView, LoadingView, MoneyText, Row, Screen, Text } from '@/shared/ui';
import { useDeleteDeposit, useDeposit, useSettleDeposit, useSettlementQuote } from '../hooks';

export default function DepositDetailScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const query = useDeposit(id);
  const deposit = query.data;
  const quote = useSettlementQuote(id, !!deposit && deposit.status !== 'SETTLED');
  const settle = useSettleDeposit(id);
  const remove = useDeleteDeposit(id);
  const [confirming, setConfirming] = useState(false);

  if (query.isPending) return <LoadingView />;
  if (query.isError || !deposit) return <ErrorView error={query.error} onRetry={() => query.refetch()} />;

  const daysLeft = deposit.maturityDate ? daysBetween(todayIso(), deposit.maturityDate) : null;

  return (
    <Screen refreshing={query.isRefetching} onRefresh={() => query.refetch()}>
      <Stack.Screen options={{ title: deposit.institutionName }} />
      <Card>
        <Text tone="muted">{t(`deposit.types.${deposit.type}`)} · {t(`deposit.statuses.${deposit.status}`)}</Text>
        <MoneyText amount={deposit.principal} currency={deposit.currency} variant="amount" />
        <Row label={t('deposit.annualRate')}>{formatPercent(deposit.annualRate)}</Row>
        {deposit.termMonths ? <Row label={t('deposit.termMonths')}>{String(deposit.termMonths)}</Row> : null}
        <Row label={t('deposit.openDate')}>{formatDate(deposit.openDate)}</Row>
        {deposit.maturityDate ? (
          <Row label={t('deposit.maturityDate')}>
            {formatDate(deposit.maturityDate)}
            {daysLeft !== null && daysLeft >= 0 && deposit.status === 'ACTIVE'
              ? ` (${t('deposit.daysLeft', { count: daysLeft })})` : ''}
          </Row>
        ) : null}
        <Row label={t('deposit.rollover')}>{t(`deposit.rollovers.${deposit.rolloverPolicy}`)}</Row>
        <Row label={t('deposit.calculation')}>{t(`deposit.calculations.${deposit.calculationMethod}`)}</Row>
        {deposit.accountNumber ? <Row label={t('deposit.accountNumber')}>{deposit.accountNumber}</Row> : null}
        {deposit.note ? <Row label={t('common.note')}>{deposit.note}</Row> : null}
      </Card>

      {deposit.status !== 'SETTLED' ? (
        <Card>
          <Row label={t('deposit.accruedInterest')}>
            <MoneyText amount={deposit.accruedInterest} currency={deposit.currency} signed />
          </Row>
          {deposit.expectedInterestAtMaturity !== null ? (
            <Row label={t('deposit.expectedInterest')}>
              <MoneyText amount={deposit.expectedInterestAtMaturity} currency={deposit.currency} />
            </Row>
          ) : null}
        </Card>
      ) : (
        <Card>
          <Text>{t('deposit.settled', { amount: formatMoney(deposit.settledAmount, deposit.currency),
            date: formatDate(deposit.settledOn) })}</Text>
        </Card>
      )}

      {quote.data ? (
        <Card>
          <Text variant="heading">{t('deposit.settleQuote')}</Text>
          <Row label={t('deposit.principal')}><MoneyText amount={quote.data.principal} currency={quote.data.currency} /></Row>
          <Row label={t('deposit.accruedInterest')}><MoneyText amount={quote.data.interest} currency={quote.data.currency} /></Row>
          <Row label="Σ"><MoneyText amount={quote.data.total} currency={quote.data.currency} variant="heading" /></Row>
          {quote.data.early ? (
            <Text tone="warning">{t('deposit.earlyWarning',
              { amount: formatMoney(quote.data.interestLostByEarlyWithdrawal, quote.data.currency) })}</Text>
          ) : null}
          {confirming ? (
            <View style={{ gap: 8 }}>
              <Button title={t('common.confirm')} variant="danger" loading={settle.isPending}
                onPress={() => settle.mutate(undefined, { onSuccess: () => setConfirming(false) })} />
              <Button title={t('common.cancel')} variant="secondary" onPress={() => setConfirming(false)} />
            </View>
          ) : (
            <Button title={t('deposit.settle')} onPress={() => setConfirming(true)} />
          )}
        </Card>
      ) : null}

      {deposit.transactions.length ? (
        <Card>
          <Text variant="heading">{t('deposit.history')}</Text>
          {deposit.transactions.map((tx) => (
            <Row key={tx.id} label={`${formatDate(tx.occurredOn)} · ${t(`deposit.transactions.${tx.type}`)}`}>
              <MoneyText amount={tx.amount} currency={deposit.currency} />
            </Row>
          ))}
        </Card>
      ) : null}

      <Button title={t('common.delete')} variant="secondary" loading={remove.isPending}
        onPress={() => remove.mutate(undefined, { onSuccess: () => router.back() })} />
    </Screen>
  );
}
