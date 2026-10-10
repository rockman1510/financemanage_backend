import { useRouter } from 'expo-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { DepositStatus } from '@/shared/api/types';
import { Button, EmptyState, ErrorView, LoadingView, Screen, SegmentedControl } from '@/shared/ui';
import { DepositCard } from '../components/DepositCard';
import { useDeposits } from '../hooks';

type Filter = 'ALL' | DepositStatus;

export default function DepositListScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const [filter, setFilter] = useState<Filter>('ACTIVE');
  const query = useDeposits(filter === 'ALL' ? undefined : filter);

  return (
    <Screen refreshing={query.isRefetching} onRefresh={() => query.refetch()}>
      <Button title={t('deposit.add')} onPress={() => router.push('/deposits/new')} />
      <SegmentedControl<Filter>
        value={filter}
        onChange={setFilter}
        options={[
          { value: 'ACTIVE', label: t('deposit.statuses.ACTIVE') },
          { value: 'MATURED', label: t('deposit.statuses.MATURED') },
          { value: 'SETTLED', label: t('deposit.statuses.SETTLED') },
          { value: 'ALL', label: '∗' },
        ]}
      />
      {query.isPending ? <LoadingView /> : null}
      {query.isError ? <ErrorView error={query.error} onRetry={() => query.refetch()} /> : null}
      {query.data?.length === 0 ? <EmptyState title={t('deposit.empty')} /> : null}
      {query.data?.map((deposit) => (
        <DepositCard key={deposit.id} deposit={deposit} onPress={() => router.push(`/deposits/${deposit.id}`)} />
      ))}
    </Screen>
  );
}
