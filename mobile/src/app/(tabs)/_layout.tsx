import Ionicons from '@expo/vector-icons/Ionicons';
import { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import type { ColorValue } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/shared/ui';

type IconName = ComponentProps<typeof Ionicons>['name'];

function icon(name: IconName) {
  return function TabIcon({ color, size }: { color: ColorValue; size: number }) {
    return <Ionicons name={name} color={color} size={size} />;
  };
}

export default function TabsLayout() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  return (
    <Tabs screenOptions={{ tabBarActiveTintColor: colors.primary }}>
      <Tabs.Screen name="index" options={{ title: t('tabs.dashboard'), tabBarIcon: icon('pie-chart-outline') }} />
      <Tabs.Screen name="deposits" options={{ title: t('tabs.deposits'), tabBarIcon: icon('wallet-outline') }} />
      <Tabs.Screen name="loans" options={{ title: t('tabs.loans'), tabBarIcon: icon('swap-horizontal-outline') }} />
      <Tabs.Screen name="settings" options={{ title: t('tabs.settings'), tabBarIcon: icon('settings-outline') }} />
    </Tabs>
  );
}
