import { Stack } from 'expo-router';
import { useTheme } from '../../theme/ThemeProvider';

export default function AppLayout() {
  const { colors } = useTheme();
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <Stack.Screen name="(tabs)" />
      <Stack.Screen name="new-message" options={{ presentation: 'modal' }} />
      <Stack.Screen name="new-group" options={{ presentation: 'modal' }} />
      <Stack.Screen name="report" options={{ presentation: 'modal' }} />
      <Stack.Screen name="search" options={{ animation: 'fade' }} />
    </Stack>
  );
}
