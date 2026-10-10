import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '@/shared/api/errorMessage';
import { newIdempotencyKey, parseAmount } from '@/shared/lib/parse';
import { Button, Card, Text, TextField } from '@/shared/ui';
import { paymentSchema, type PaymentValues } from '../schemas';

interface PaymentFormProps {
  title: string;
  defaultAmount?: number | null;
  today: string;
  pending: boolean;
  error: unknown;
  onSubmit: (input: { amount: number; paidOn: string; idempotencyKey: string }) => void;
}

/** Amount + date form. Keeps one idempotency key per form so a retried submit is not applied twice. */
export function PaymentForm({ title, defaultAmount, today, pending, error, onSubmit }: PaymentFormProps) {
  const { t } = useTranslation();
  const [idempotencyKey] = useState(newIdempotencyKey);
  const { control, handleSubmit } = useForm<PaymentValues>({
    resolver: zodResolver(paymentSchema),
    defaultValues: { amount: defaultAmount ? String(defaultAmount) : '', paidOn: today },
  });
  const submit = handleSubmit((values) =>
    onSubmit({ amount: parseAmount(values.amount), paidOn: values.paidOn, idempotencyKey }));

  return (
    <Card>
      <Text variant="heading">{title}</Text>
      <Controller control={control} name="amount" render={({ field, fieldState }) => (
        <TextField label={t('loan.amount')} value={field.value} onChangeText={field.onChange} onBlur={field.onBlur}
          keyboardType="decimal-pad" error={fieldState.error?.message && t(fieldState.error.message)} />
      )} />
      <Controller control={control} name="paidOn" render={({ field, fieldState }) => (
        <TextField label={t('loan.paidOn')} value={field.value} onChangeText={field.onChange} onBlur={field.onBlur}
          error={fieldState.error?.message && t(fieldState.error.message)} />
      )} />
      {error ? (
        <Text tone="negative">{errorMessage(error, t)}</Text>
      ) : null}
      <Button title={t('common.save')} onPress={submit} loading={pending} />
    </Card>
  );
}
