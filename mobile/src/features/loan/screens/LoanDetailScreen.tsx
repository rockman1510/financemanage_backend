import { zodResolver } from '@hookform/resolvers/zod';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { features } from '@/shared/config/features';
import { formatDate, formatPercent, todayIso } from '@/shared/lib/format';
import { parseAmount } from '@/shared/lib/parse';
import {
  Button, Card, ErrorView, LoadingView, MoneyText, Row, Screen, SegmentedControl, Text, TextField,
} from '@/shared/ui';
import { InstallmentRow } from '../components/InstallmentRow';
import { PaymentForm } from '../components/PaymentForm';
import { useChangeLoanRate, useDeleteLoan, useLoan, usePrepayLoan, useRepayLoan } from '../hooks';
import { rateChangeSchema, type RateChangeValues } from '../schemas';

type Panel = 'repay' | 'prepay' | 'rate';

export default function LoanDetailScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const query = useLoan(id);
  const repay = useRepayLoan(id);
  const prepay = usePrepayLoan(id);
  const changeRate = useChangeLoanRate(id);
  const remove = useDeleteLoan(id);
  const [panel, setPanel] = useState<Panel>('repay');
  const today = todayIso();
  const rateForm = useForm<RateChangeValues>({
    resolver: zodResolver(rateChangeSchema),
    defaultValues: { annualRate: '', effectiveFrom: today },
  });

  if (query.isPending) return <LoadingView />;
  if (query.isError || !query.data) return <ErrorView error={query.error} onRetry={() => query.refetch()} />;

  const { summary, installments, repayments, ratePeriods } = query.data;
  const currency = summary.currency;
  const active = summary.status === 'ACTIVE';
  const panels: { value: Panel; label: string }[] = [{ value: 'repay', label: t('loan.repay') }];
  if (features.loanPrepayment) panels.push({ value: 'prepay', label: t('loan.prepay') });
  if (features.loanRateChange && summary.repaymentMethod !== 'INTEREST_FREE') {
    panels.push({ value: 'rate', label: t('loan.changeRate') });
  }

  return (
    <Screen refreshing={query.isRefetching} onRefresh={() => query.refetch()}>
      <Stack.Screen options={{ title: summary.counterpartyName }} />
      <Card>
        <Text tone="muted">{t(`loan.directions.${summary.direction}`)} · {t(`loan.types.${summary.type}`)} · {t(`loan.statuses.${summary.status}`)}</Text>
        <Text variant="caption" tone="muted">{t('loan.outstanding')}</Text>
        <MoneyText amount={summary.outstandingPrincipal} currency={currency} variant="amount" />
        <Row label={t('loan.principal')}><MoneyText amount={summary.principal} currency={currency} /></Row>
        <Row label={t('loan.annualRate')}>{formatPercent(summary.currentRate)}</Row>
        <Row label={t('loan.method')}>{t(`loan.methods.${summary.repaymentMethod}`)}</Row>
        <Row label={t('loan.startDate')}>{formatDate(summary.startDate)}</Row>
        <Row label={t('loan.totalInterest')}><MoneyText amount={query.data.totalScheduledInterest} currency={currency} /></Row>
        {ratePeriods.length > 1 ? (
          <Row label={t('loan.changeRate')}>
            {ratePeriods.map((p) => `${formatDate(p.effectiveFrom)}: ${formatPercent(p.annualRate)}`).join('\n')}
          </Row>
        ) : null}
        {summary.note ? <Row label={t('common.note')}>{summary.note}</Row> : null}
      </Card>

      {active ? (
        <>
          <SegmentedControl value={panel} onChange={setPanel} options={panels} />
          {panel === 'repay' ? (
            <PaymentForm key={`repay-${repayments.length}`} title={t('loan.repay')} today={today}
              defaultAmount={summary.nextDueAmount} pending={repay.isPending} error={repay.error}
              onSubmit={(input) => repay.mutate(input)} />
          ) : null}
          {panel === 'prepay' ? (
            <PaymentForm key={`prepay-${repayments.length}`} title={`${t('loan.prepay')} (${t('loan.earlyFee')}: ${formatPercent(query.data.earlyRepaymentFeeRate)})`}
              today={today} pending={prepay.isPending} error={prepay.error}
              onSubmit={(input) => prepay.mutate(input)} />
          ) : null}
          {panel === 'rate' ? (
            <Card>
              <Controller control={rateForm.control} name="annualRate" render={({ field, fieldState }) => (
                <TextField label={t('loan.annualRate')} value={field.value} onChangeText={field.onChange}
                  keyboardType="decimal-pad" error={fieldState.error?.message && t(fieldState.error.message)} />
              )} />
              <Controller control={rateForm.control} name="effectiveFrom" render={({ field, fieldState }) => (
                <TextField label={t('loan.effectiveFrom')} value={field.value} onChangeText={field.onChange}
                  error={fieldState.error?.message && t(fieldState.error.message)} />
              )} />
              <Button title={t('common.save')} loading={changeRate.isPending}
                onPress={rateForm.handleSubmit((v) => changeRate.mutate({
                  effectiveFrom: v.effectiveFrom, annualRate: parseAmount(v.annualRate) }))} />
            </Card>
          ) : null}
        </>
      ) : null}

      <Card>
        <Text variant="heading">{t('loan.schedule')}</Text>
        {installments.map((installment) => (
          <InstallmentRow key={installment.id} installment={installment} currency={currency} />
        ))}
      </Card>

      {repayments.length ? (
        <Card>
          <Text variant="heading">{t('loan.repayments')}</Text>
          {repayments.map((r) => (
            <Row key={r.id} label={`${formatDate(r.paidOn)}${r.type === 'PREPAYMENT' ? ` · ${t('loan.prepay')}` : ''}`}>
              <MoneyText amount={r.amount} currency={currency} />
            </Row>
          ))}
        </Card>
      ) : null}

      <Button title={t('common.delete')} variant="secondary" loading={remove.isPending}
        onPress={() => remove.mutate(undefined, { onSuccess: () => router.back() })} />
    </Screen>
  );
}
