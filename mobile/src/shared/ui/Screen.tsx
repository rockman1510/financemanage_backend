import type { ReactNode } from 'react';
import { RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { useTheme } from './theme';

interface ScreenProps {
  children: ReactNode;
  scroll?: boolean;
  refreshing?: boolean;
  onRefresh?: () => void;
}

export function Screen({ children, scroll = true, refreshing = false, onRefresh }: ScreenProps) {
  const { colors, spacing } = useTheme();
  if (!scroll) {
    return <View style={[styles.fill, { backgroundColor: colors.background, padding: spacing.lg }]}>{children}</View>;
  }
  return (
    <ScrollView
      style={[styles.fill, { backgroundColor: colors.background }]}
      contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}
      keyboardShouldPersistTaps="handled"
      refreshControl={onRefresh ? <RefreshControl refreshing={refreshing} onRefresh={onRefresh} /> : undefined}
    >
      {children}
    </ScrollView>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
