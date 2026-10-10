import { useColorScheme } from 'react-native';

/** Design tokens. Re-brand the app by editing this file only. */
const palette = {
  green600: '#0F9D58',
  green50: '#E8F5EE',
  red600: '#D93025',
  red50: '#FDECEA',
  amber600: '#F29900',
  amber50: '#FEF7E0',
  blue600: '#1A73E8',
  blue50: '#E8F0FE',
  gray900: '#1F2328',
  gray700: '#424A53',
  gray500: '#6E7781',
  gray300: '#D0D7DE',
  gray100: '#F3F4F6',
  white: '#FFFFFF',
  black: '#0D1117',
  gray800: '#21262D',
};

export const lightColors = {
  background: palette.gray100,
  surface: palette.white,
  text: palette.gray900,
  textMuted: palette.gray500,
  border: palette.gray300,
  primary: palette.blue600,
  primarySoft: palette.blue50,
  onPrimary: palette.white,
  positive: palette.green600,
  positiveSoft: palette.green50,
  negative: palette.red600,
  negativeSoft: palette.red50,
  warning: palette.amber600,
  warningSoft: palette.amber50,
};

export type ColorTokens = typeof lightColors;

export const darkColors: ColorTokens = {
  background: palette.black,
  surface: palette.gray800,
  text: '#E6EDF3',
  textMuted: '#8B949E',
  border: '#30363D',
  primary: '#4C8DF6',
  primarySoft: '#16233A',
  onPrimary: palette.white,
  positive: '#3FB950',
  positiveSoft: '#12261A',
  negative: '#F85149',
  negativeSoft: '#2D1517',
  warning: '#D29922',
  warningSoft: '#2B2111',
};

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;
export const radius = { sm: 6, md: 10, lg: 16, pill: 999 } as const;
export const typography = {
  title: { fontSize: 22, fontWeight: '700' as const },
  heading: { fontSize: 17, fontWeight: '600' as const },
  body: { fontSize: 15 },
  caption: { fontSize: 13 },
  amount: { fontSize: 28, fontWeight: '700' as const },
};

export interface Theme {
  colors: ColorTokens;
  spacing: typeof spacing;
  radius: typeof radius;
  typography: typeof typography;
  dark: boolean;
}

export function useTheme(): Theme {
  const dark = useColorScheme() === 'dark';
  return { colors: dark ? darkColors : lightColors, spacing, radius, typography, dark };
}
