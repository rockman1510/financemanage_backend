import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'expo-router';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { ApiError } from '@/shared/api/ApiError';
import { todayIso } from '@/shared/lib/format';
import { Button, Card, MoneyText, Row, Screen, SegmentedControl, Text, TextField } from '@/shared/ui';
import { InstallmentRow } from '../components/InstallmentRow';
import { useCreateLoan, useSchedulePreview } from '../hooks';
import { defaultLoanForm, loanFormSchema, type LoanFormValues, toCreateLoanRequest } from '../schemas';

export default function LoanFormScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const create = useCreateLoan();
  const preview = useSchedulePreview();
  const { control, handleSubmit } = useForm<LoanFormValues>({
    resolver: zodResolver(loanFormSchema),
    defaultValues: defaultLoanForm(todayIso()),
  });
  const method = useWatch({ control, name: 'repaymentMethod' });
  const currency = useWatch({ control, name: 'currency' });
  const errorText = (message?: string) => (message ? t(message) : undefined);

  const onSave = handleSubmit((values) =>
    create.mutate(toCreateLoanRequest(values), {
      onSuccess: (loan) => router.replace(`/loans/${loan.summary.id}`),
    }));
  const onPreview = handleSubmit((values) => preview.mutate(toCreateLoanRequest(values)));

  return (
    <Screen>
      <Controller control={control} name="direction" render={({ field }) => (
        <SegmentedControl label={t('loan.direction')} value={field.value} onChange={field.onChange}
          options={(['BORROW', 'LEND'] as const).map((v) => ({ value: v, label: t(`loan.directions.${v}`) }))} />
      )} />
      <Controller control={control} name="counterpartyName" render={({ field, fieldState }) => (
        <TextField label={t('loan.counterparty')} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="type" render={({ field }) => (
        <SegmentedControl label={t('loan.type')} value={field.value} onChange={field.onChange}
          options={(['UNSECURED', 'MORTGAGE', 'INSTALLMENT', 'PERSONAL'] as const)
            .map((v) => ({ value: v, label: t(`loan.types.${v}`) }))} />
      )} />
      <Controller control={control} name="principal" render={({ field, fieldState }) => (
        <TextField label={`${t('loan.principal')} (${currency})`} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} keyboardType="decimal-pad" error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="currency" render={({ field }) => (
        <SegmentedControl label={t('common.currency')} value={field.value} onChange={field.onChange}
          options={['VND', 'USD'].map((v) => ({ value: v, label: v }))} />
      )} />
      <Controller control={control} name="repaymentMethod" render={({ field }) => (
        <SegmentedControl label={t('loan.method')} value={field.value} onChange={field.onChange}
          options={(['EQUAL_PRINCIPAL', 'ANNUITY', 'BULLET', 'INTEREST_FREE'] as const)
            .map((v) => ({ value: v, label: t(`loan.methods.${v}`) }))} />
      )} />
      {method !== 'INTEREST_FREE' ? (
        <Controller control={control} name="annualRate" render={({ field, fieldState }) => (
          <TextField label={t('loan.annualRate')} value={field.value} onChangeText={field.onChange}
            onBlur={field.onBlur} keyboardType="decimal-pad" error={errorText(fieldState.error?.message)} />
        )} />
      ) : null}
      <Controller control={control} name="termMonths" render={({ field, fieldState }) => (
        <TextField label={t('loan.termMonths')} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} keyboardType="number-pad" error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="startDate" render={({ field, fieldState }) => (
        <TextField label={t('loan.startDate')} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} placeholder="YYYY-MM-DD" error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="earlyRepaymentFeeRate" render={({ field, fieldState }) => (
        <TextField label={t('loan.earlyFee')} value={field.value} onChangeText={field.onChange}
          onBlur={field.onBlur} keyboardType="decimal-pad" error={errorText(fieldState.error?.message)} />
      )} />
      <Controller control={control} name="note" render={({ field }) => (
        <TextField label={t('common.note')} value={field.value} onChangeText={field.onChange} multiline />
      )} />

      {preview.data ? (
        <Card>
          <Row label={t('loan.totalInterest')}>
            <MoneyText amount={preview.data.totalInterest} currency={preview.data.currency} />
          </Row>
          <Row label={t('loan.totalPayment')}>
            <MoneyText amount={preview.data.totalPayment} currency={preview.data.currency} variant="heading" />
          </Row>
          {preview.data.installments.slice(0, 12).map((i) => (
            <InstallmentRow key={i.id} installment={i} currency={preview.data.currency} />
          ))}
        </Card>
      ) : null}
      {create.error ? (
        <Text tone="negative">{create.error instanceof ApiError ? create.error.message : t('common.unexpectedError')}</Text>
      ) : null}
      <Button title={t('common.preview')} variant="secondary" onPress={onPreview} loading={preview.isPending} />
      <Button title={t('common.save')} onPress={onSave} loading={create.isPending} />
    </Screen>
  );
}
