import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';
import { Text } from './Text';
import { useTheme } from './theme';

interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
  loading?: boolean;
  disabled?: boolean;
}

export function Button({ title, onPress, variant = 'primary', loading = false, disabled = false }: ButtonProps) {
  const { colors, radius, spacing } = useTheme();
  const inactive = disabled || loading;
  const background = { primary: colors.primary, secondary: colors.primarySoft, danger: colors.negative }[variant];
  const foreground = variant === 'secondary' ? colors.primary : colors.onPrimary;
  return (
    <Pressable
      role="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: inactive, busy: loading }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: background, borderRadius: radius.md, paddingVertical: spacing.md },
        (pressed || inactive) && { opacity: 0.6 },
      ]}
    >
      {loading ? (
        <ActivityIndicator color={foreground} />
      ) : (
        <Text variant="heading" style={{ color: foreground }}>
          {title}
        </Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({ button: { alignItems: 'center', justifyContent: 'center', minHeight: 48 } });
