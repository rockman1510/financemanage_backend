import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { Text } from './Text';

const isPrimitive = (value: unknown) => typeof value === 'string' || typeof value === 'number';

/** Label on the left, value on the right. Plain strings/numbers (or a list of them) are wrapped in Text. */
export function Row({ label, children }: { label: string; children: ReactNode }) {
  const textual = isPrimitive(children) || (Array.isArray(children) && children.every(isPrimitive));
  return (
    <View style={styles.row}>
      <Text tone="muted">{label}</Text>
      <View style={styles.value}>{textual ? <Text>{children}</Text> : children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  value: { flexShrink: 1, alignItems: 'flex-end' },
});
