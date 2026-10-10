import { View } from 'react-native';
import { Text } from './Text';
import { useTheme } from './theme';

export type BadgeTone = 'neutral' | 'positive' | 'negative' | 'warning' | 'primary';

export function Badge({ label, tone = 'neutral' }: { label: string; tone?: BadgeTone }) {
  const { colors, radius, spacing } = useTheme();
  const palette = {
    neutral: [colors.background, colors.textMuted],
    positive: [colors.positiveSoft, colors.positive],
    negative: [colors.negativeSoft, colors.negative],
    warning: [colors.warningSoft, colors.warning],
    primary: [colors.primarySoft, colors.primary],
  }[tone];
  return (
    <View style={{ backgroundColor: palette[0], borderRadius: radius.pill, paddingHorizontal: spacing.sm,
      paddingVertical: 2, alignSelf: 'flex-start' }}>
      <Text variant="caption" style={{ color: palette[1], fontWeight: '600' }}>
        {label}
      </Text>
    </View>
  );
}
