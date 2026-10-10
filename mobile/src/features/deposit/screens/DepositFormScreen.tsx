import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'expo-router';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '@/shared/api/errorMessage';
import { formatDate, formatMoney, todayIso } from '@/shared/lib/format';
import { Button, Card, MoneyText, Row, Screen, SegmentedControl, Text, TextField } from '@/shared/ui';
import { useCreateDeposit, useDepositPreview } from '../hooks';
import { defaultDepositForm, depositFormSchema, type DepositFormValues, toCreateDepositRequest } from '../schemas';

export default function DepositFormScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const create = useCreateDeposit();
  const preview = useDepositPreview();
  const { control, handleSubmit } = useForm<DepositFormValues>({
    resolver: zodResolver(depositFormSchema),
    defaultValues: defaultDepositForm(todayIso()),
  });
  const type = useWatch({ control, name: 'type' });
  const currency = useWatch({ control, name: 'currency' });
  const errorText = (message?: string) => (message ? t(message) : undefined);

  const onSave = handleSubmit((values) =>
    create.mutate(toCreateDepositRequest(values), {
      onSuccess: (deposit) => router.replace(`/deposits/${deposit.id}`),
    }));
  const onPreview = handleSubmit((values) => preview.mutate(toCreateDepositRequest(values)));

  return (
    <Screen>
      <Controller control={control} name="institutionName" render={({ field, fieldState }) => (
        <TextField label={t('deposit.institution')} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="type" render={({ field }) => (
        <SegmentedControl label={t('deposit.type')} value={field.value} onChange={field.onChange}
          options={(['TERM', 'DEMAND'] as const).map((v) => ({ value: v, label: t(`deposit.types.${v}`) }))} />
      )} />
      <Controller control={control} name="principal" render={({ field, fieldState }) => (
        <TextField label={`${t('deposit.principal')} (${currency})`} value={field.value}
          onChangeText={field.onChange} onBlur={field.onBlur} keyboardType="decimal-pad"
          placeholder="100.000.000" error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="currency" render={({ field }) => (
        <SegmentedControl label={t('common.currency')} value={field.value} onChange={field.onChange}
          options={['VND', 'USD'].map((v) => ({ value: v, label: v }))} />
      )} />
      <Controller control={control} name="annualRate" render={({ field, fieldState }) => (
        <TextField label={t('deposit.annualRate')} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} keyboardType="decimal-pad" placeholder="5.5"
          error={errorText(fieldState.error?.message)} />
      )} />
      {type === 'TERM' ? (
        <>
          <Controller control={control} name="termMonths" render={({ field, fieldState }) => (
            <TextField label={t('deposit.termMonths')} value={field.value} onChangeText={field.onChange}
              onBlur={field.onBlur} keyboardType="number-pad" error={errorText(fieldState.error?.message)} />
          )} />
          <Controller control={control} name="earlyWithdrawalRate" render={({ field, fieldState }) => (
            <TextField label={t('deposit.earlyWithdrawalRate')} value={field.value} onChangeText={field.onChange}
              onBlur={field.onBlur} keyboardType="decimal-pad" error={errorText(fieldState.error?.message)} />
          )} />
          <Controller control={control} name="rolloverPolicy" render={({ field }) => (
            <SegmentedControl label={t('deposit.rollover')} value={field.value} onChange={field.onChange}
              options={(['NONE', 'PRINCIPAL_ONLY', 'PRINCIPAL_AND_INTEREST'] as const)
                .map((v) => ({ value: v, label: t(`deposit.rollovers.${v}`) }))} />
          )} />
          <Controller control={control} name="interestPaymentMethod" render={({ field }) => (
            <SegmentedControl label={t('deposit.interestPayment')} value={field.value} onChange={field.onChange}
              options={(['AT_MATURITY', 'MONTHLY', 'UPFRONT'] as const)
                .map((v) => ({ value: v, label: t(`deposit.interestPayments.${v}`) }))} />
          )} />
        </>
      ) : null}
      <Controller control={control} name="calculationMethod" render={({ field }) => (
        <SegmentedControl label={t('deposit.calculation')} value={field.value} onChange={field.onChange}
          options={(['SIMPLE_ACTUAL_365', 'SIMPLE_30_360', 'COMPOUND_MONTHLY'] as const)
            .map((v) => ({ value: v, label: t(`deposit.calculations.${v}`) }))} />
      )} />
      <Controller control={control} name="openDate" render={({ field, fieldState }) => (
        <TextField label={t('deposit.openDate')} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} placeholder="YYYY-MM-DD" error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="accountNumber" render={({ field }) => (
        <TextField label={t('deposit.accountNumber')} value={field.value} onChangeText={field.onChange} />
      )} />
      <Controller control={control} name="note" render={({ field }) => (
        <TextField label={t('common.note')} value={field.value} onChangeText={field.onChange} multiline />
      )} />

      {preview.data ? (
        <Card>
          {preview.data.maturityDate ? (
            <Row label={t('deposit.maturityDate')}>{formatDate(preview.data.maturityDate)}</Row>
          ) : null}
          <Row label={t('deposit.expectedInterest')}>
            <MoneyText amount={preview.data.expectedInterest} currency={preview.data.currency} signed />
          </Row>
          <Row label={t('deposit.totalAtMaturity')}>
            <Text variant="heading">{formatMoney(preview.data.totalAtMaturity, preview.data.currency)}</Text>
          </Row>
        </Card>
      ) : null}
      {create.error ? (
        <Text tone="negative">{errorMessage(create.error, t)}</Text>
      ) : null}
      <Button title={t('common.preview')} variant="secondary" onPress={onPreview} loading={preview.isPending} />
      <Button title={t('common.save')} onPress={onSave} loading={create.isPending} />
    </Screen>
  );
}
