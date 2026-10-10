import { useRouter } from 'expo-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { LoanDirection } from '@/shared/api/types';
import { Button, EmptyState, ErrorView, LoadingView, Screen, SegmentedControl } from '@/shared/ui';
import { LoanCard } from '../components/LoanCard';
import { useLoans } from '../hooks';

type Filter = 'ALL' | LoanDirection;

export default function LoanListScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const [filter, setFilter] = useState<Filter>('ALL');
  const query = useLoans(filter === 'ALL' ? undefined : filter);

  return (
    <Screen refreshing={query.isRefetching} onRefresh={() => query.refetch()}>
      <Button title={t('loan.add')} onPress={() => router.push('/loans/new')} />
      <SegmentedControl<Filter>
        value={filter}
        onChange={setFilter}
        options={[
          { value: 'ALL', label: '∗' },
          { value: 'BORROW', label: t('loan.directions.BORROW') },
          { value: 'LEND', label: t('loan.directions.LEND') },
        ]}
      />
      {query.isPending ? <LoadingView /> : null}
      {query.isError ? <ErrorView error={query.error} onRetry={() => query.refetch()} /> : null}
      {query.data?.length === 0 ? <EmptyState title={t('loan.empty')} /> : null}
      {query.data?.map((loan) => (
        <LoanCard key={loan.id} loan={loan} onPress={() => router.push(`/loans/${loan.id}`)} />
      ))}
    </Screen>
  );
}
