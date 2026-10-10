import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { View } from 'react-native';
import { ApiError } from '@/shared/api/ApiError';
import { errorMessage } from '@/shared/api/errorMessage';
import { useSession } from '@/shared/session/sessionStore';
import { Button, Screen, Text, TextField } from '@/shared/ui';
import { authApi } from '../api';
import { loginSchema, type LoginValues } from '../schemas';

export default function LoginScreen() {
  const { t } = useTranslation();
  const setTokens = useSession((s) => s.setTokens);
  const [formError, setFormError] = useState<string | null>(null);
  const { control, handleSubmit, formState } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await setTokens(await authApi.login(values.email, values.password));
    } catch (error) {
      setFormError(error instanceof ApiError && error.status === 401 ? t('auth.invalidCredentials')
        : errorMessage(error, t));
    }
  });

  return (
    <Screen>
      <View style={{ gap: 16, paddingTop: 48 }}>
        <Text variant="title">Finance Manage</Text>
        <Text tone="muted">{t('auth.welcome')}</Text>
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
        <Button title={t('auth.login')} onPress={onSubmit} loading={formState.isSubmitting} />
        <Link href="/register" style={{ textAlign: 'center' }}>
          <Text tone="primary">{t('auth.noAccount')}</Text>
        </Link>
      </View>
    </Screen>
  );
}
