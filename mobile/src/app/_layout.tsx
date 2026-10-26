import {
  PlusJakartaSans_400Regular, PlusJakartaSans_400Regular_Italic, PlusJakartaSans_500Medium,
  PlusJakartaSans_600SemiBold, PlusJakartaSans_700Bold, PlusJakartaSans_800ExtraBold, useFonts,
} from '@expo-google-fonts/plus-jakarta-sans';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ApiError } from '../api/client';
import { LiveProvider } from '../live/LiveProvider';
import { PeopleProvider } from '../lib/people';
import { SessionProvider, useSession } from '../session/SessionProvider';
import { ThemeProvider, useTheme } from '../theme/ThemeProvider';

void SplashScreen.preventAutoHideAsync().catch(() => undefined);

function Routes() {
  const { state } = useSession();
  const { scheme, colors } = useTheme();
  const signedIn = state === 'signedIn';
  useEffect(() => {
    if (state !== 'restoring') {
      void SplashScreen.hideAsync().catch(() => undefined);
    }
  }, [state]);
  if (state === 'restoring') {
    return null;
  }
  return (
    <>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
        <Stack.Protected guard={signedIn}>
          <Stack.Screen name="(app)" />
        </Stack.Protected>
        <Stack.Protected guard={!signedIn}>
          <Stack.Screen name="(auth)" />
        </Stack.Protected>
        {/* the links of the emails open these, signed in or not */}
        <Stack.Screen name="verify-email" />
        <Stack.Screen name="reset-password" />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    PlusJakartaSans_400Regular, PlusJakartaSans_400Regular_Italic, PlusJakartaSans_500Medium,
    PlusJakartaSans_600SemiBold, PlusJakartaSans_700Bold, PlusJakartaSans_800ExtraBold,
  });
  const [client] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // a refusal is an answer: asked again only when offline or Oro restarts
        retry: (count, e) => count < 3 && (!(e instanceof ApiError) || e.offline || e.status >= 500),
      },
    },
  }));
  if (!fontsLoaded) {
    return null;
  }
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <QueryClientProvider client={client}>
          <SessionProvider>
            <PeopleProvider>
              <LiveProvider>
                <Routes />
              </LiveProvider>
            </PeopleProvider>
          </SessionProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
