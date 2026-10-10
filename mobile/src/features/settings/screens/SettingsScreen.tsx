import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { authApi } from '@/features/auth/api';
import { queryKeys } from '@/shared/api/queryKeys';
import { env } from '@/shared/config/env';
import { useSession } from '@/shared/session/sessionStore';
import { Button, Card, Row, Screen, SegmentedControl, Text } from '@/shared/ui';
import { type Language } from '@/i18n';

export default function SettingsScreen() {
  const { t, i18n } = useTranslation();
  const router = useRouter();
  const { refreshToken, signOut } = useSession();
  const profile = useQuery({ queryKey: queryKeys.profile, queryFn: authApi.me });

  const logout = async () => {
    if (refreshToken) {
      await authApi.logout(refreshToken).catch(() => undefined);
    }
    await signOut();
  };

  return (
    <Screen>
      <Card>
        <Text variant="heading">{t('settings.profile')}</Text>
        {profile.data ? (
          <>
            <Text>{profile.data.fullName}</Text>
            <Text tone="muted">{profile.data.email}</Text>
          </>
        ) : null}
      </Card>
      <Card>
        <SegmentedControl<Language> label={t('settings.language')} value={i18n.language as Language}
          onChange={(lng) => i18n.changeLanguage(lng)}
          options={[{ value: 'vi', label: 'Tiếng Việt' }, { value: 'en', label: 'English' }]} />
      </Card>
      <Card onPress={() => router.push('/notifications')}>
        <Text variant="heading">{t('settings.notifications')}</Text>
      </Card>
      <Card>
        <Row label={t('settings.server')}>{env.apiUrl}</Row>
      </Card>
      <Button title={t('settings.logout')} variant="danger" onPress={logout} />
    </Screen>
  );
}
