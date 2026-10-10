import { ActivityIndicator, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/ApiError';
import { Button } from './Button';
import { Text } from './Text';
import { useTheme } from './theme';

export function LoadingView() {
  const { colors } = useTheme();
  return (
    <View style={{ padding: 32, alignItems: 'center' }}>
      <ActivityIndicator accessibilityLabel="loading" color={colors.primary} />
    </View>
  );
}

export function EmptyState({ title, message, action }: { title: string; message?: string;
  action?: { label: string; onPress: () => void } }) {
  return (
    <View style={{ padding: 32, alignItems: 'center', gap: 8 }}>
      <Text variant="heading">{title}</Text>
      {message ? <Text tone="muted">{message}</Text> : null}
      {action ? <Button title={action.label} onPress={action.onPress} variant="secondary" /> : null}
    </View>
  );
}

export function ErrorView({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  const message = error instanceof ApiError ? error.message : t('common.unexpectedError');
  return (
    <View style={{ padding: 32, alignItems: 'center', gap: 8 }}>
      <Text tone="negative">{message}</Text>
      {onRetry ? <Button title={t('common.retry')} onPress={onRetry} variant="secondary" /> : null}
    </View>
  );
}
