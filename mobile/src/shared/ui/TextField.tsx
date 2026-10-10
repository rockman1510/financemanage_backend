import { StyleSheet, TextInput, View, type KeyboardTypeOptions } from 'react-native';
import { Text } from './Text';
import { useTheme } from './theme';

interface TextFieldProps {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  onBlur?: () => void;
  error?: string;
  placeholder?: string;
  keyboardType?: KeyboardTypeOptions;
  secureTextEntry?: boolean;
  autoCapitalize?: 'none' | 'sentences' | 'words';
  multiline?: boolean;
}

export function TextField({ label, error, ...input }: TextFieldProps) {
  const { colors, radius, spacing } = useTheme();
  return (
    <View style={{ gap: spacing.xs }}>
      <Text variant="caption" tone="muted">
        {label}
      </Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={colors.textMuted}
        style={[
          styles.input,
          {
            color: colors.text,
            backgroundColor: colors.surface,
            borderColor: error ? colors.negative : colors.border,
            borderRadius: radius.md,
            padding: spacing.md,
          },
          input.multiline && { minHeight: 80, textAlignVertical: 'top' },
        ]}
        {...input}
      />
      {error ? (
        <Text variant="caption" tone="negative">
          {error}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({ input: { borderWidth: 1, fontSize: 16 } });
