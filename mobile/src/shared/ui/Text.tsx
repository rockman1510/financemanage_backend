import type { ReactNode } from 'react';
import { Text as RNText, type TextStyle } from 'react-native';
import { useTheme } from './theme';

type Variant = 'title' | 'heading' | 'body' | 'caption' | 'amount';
type Tone = 'default' | 'muted' | 'positive' | 'negative' | 'warning' | 'primary';

interface TextProps {
  children: ReactNode;
  variant?: Variant;
  tone?: Tone;
  style?: TextStyle;
  numberOfLines?: number;
}

export function Text({ children, variant = 'body', tone = 'default', style, numberOfLines }: TextProps) {
  const { colors, typography } = useTheme();
  const color = {
    default: colors.text,
    muted: colors.textMuted,
    positive: colors.positive,
    negative: colors.negative,
    warning: colors.warning,
    primary: colors.primary,
  }[tone];
  return (
    <RNText numberOfLines={numberOfLines} style={[typography[variant], { color }, style]}>
      {children}
    </RNText>
  );
}
