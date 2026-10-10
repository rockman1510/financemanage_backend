import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { View } from 'react-native';
import { ApiError } from '@/shared/api/ApiError';
import { useSession } from '@/shared/session/sessionStore';
import { Button, Screen, Text, TextField } from '@/shared/ui';
import { authApi } from '../api';
import { registerSchema, type RegisterValues } from '../schemas';

export default function RegisterScreen() {
  const { t } = useTranslation();
  const setTokens = useSession((s) => s.setTokens);
  const [formError, setFormError] = useState<string | null>(null);
  const { control, handleSubmit, formState } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await setTokens(await authApi.register(values.email, values.password, values.fullName));
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : t('common.unexpectedError'));
    }
  });

  return (
    <Screen>
      <View style={{ gap: 16, paddingTop: 48 }}>
        <Text variant="title">{t('auth.register')}</Text>
        <Controller control={control} name="fullName" render={({ field, fieldState }) => (
          <TextField label={t('auth.fullName')} value={field.value} onChangeText={field.onChange}
            onBlur={field.onBlur} autoCapitalize="words"
            error={fieldState.error?.message && t(fieldState.error.message)} />
        )} />
        <Controller control={control} name="email" render={({ field, fieldState }) => (
          <TextField label={t('auth.email')} value={field.value} onChangeText={field.onChange} onBlur={field.onBlur}
            keyboardType="email-address" autoCapitalize="none"
            error={fieldState.error?.message && t(fieldState.error.message)} />
        )} />
        <Controller control={control} name="password" render={({ field, fieldState }) => (
          <TextField label={t('auth.password')} value={field.value} onChangeText={field.onChange}
            onBlur={field.onBlur} secureTextEntry
            error={fieldState.error?.message && t(fieldState.error.message)} />
        )} />
        {formError ? <Text tone="negative">{formError}</Text> : null}
        <Button title={t('auth.register')} onPress={onSubmit} loading={formState.isSubmitting} />
        <Link href="/login" style={{ textAlign: 'center' }}>
          <Text tone="primary">{t('auth.haveAccount')}</Text>
        </Link>
      </View>
    </Screen>
  );
}
