import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { queryKeys } from '@/shared/api/queryKeys';
import { formatDate } from '@/shared/lib/format';
import { Card, EmptyState, ErrorView, LoadingView, Screen, Text } from '@/shared/ui';
import { notificationApi } from '../api';

export default function NotificationsScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const client = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.notifications, queryFn: notificationApi.list });
  const markRead = useMutation({
    mutationFn: notificationApi.markRead,
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.notifications }),
  });

  if (query.isPending) return <LoadingView />;
  if (query.isError) return <ErrorView error={query.error} onRetry={() => query.refetch()} />;

  return (
    <Screen refreshing={query.isRefetching} onRefresh={() => query.refetch()}>
      {query.data.length === 0 ? <EmptyState title={t('notifications.empty')} /> : null}
      {query.data.map((n) => (
        <Card key={n.id} onPress={() => {
          if (!n.read) markRead.mutate(n.id);
          if (n.referenceId) router.push(n.referenceType === 'LOAN' ? `/loans/${n.referenceId}` : `/deposits/${n.referenceId}`);
        }}>
          <Text variant="heading" tone={n.read ? 'muted' : 'default'}>{n.title}</Text>
          <Text tone="muted">{n.body}</Text>
          <Text variant="caption" tone="muted">{formatDate(n.createdAt)}</Text>
        </Card>
      ))}
    </Screen>
  );
}
