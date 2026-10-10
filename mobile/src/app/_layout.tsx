import '@/i18n';
import { QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { createQueryClient } from '@/shared/api/queryClient';
import { useSession } from '@/shared/session/sessionStore';
import { LoadingView } from '@/shared/ui';

export default function RootLayout() {
  const { t } = useTranslation();
  const [queryClient] = useState(createQueryClient);
  const status = useSession((s) => s.status);
  const hydrate = useSession((s) => s.hydrate);

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (status === 'signedOut') {
      queryClient.clear(); // never show the previous user's data
    }
  }, [status, queryClient]);

  if (status === 'loading') {
    return <LoadingView />;
  }

  return (
    <QueryClientProvider client={queryClient}>
      <StatusBar style="auto" />
      <Stack>
        <Stack.Protected guard={status === 'signedIn'}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="deposits/new" options={{ title: t('deposit.add'), presentation: 'modal' }} />
          <Stack.Screen name="deposits/[id]" options={{ title: t('deposit.title') }} />
          <Stack.Screen name="loans/new" options={{ title: t('loan.add'), presentation: 'modal' }} />
          <Stack.Screen name="loans/[id]" options={{ title: t('loan.title') }} />
          <Stack.Screen name="notifications" options={{ title: t('notifications.title') }} />
        </Stack.Protected>
        <Stack.Protected guard={status === 'signedOut'}>
          <Stack.Screen name="(auth)" options={{ headerShown: false }} />
        </Stack.Protected>
      </Stack>
    </QueryClientProvider>
  );
}
