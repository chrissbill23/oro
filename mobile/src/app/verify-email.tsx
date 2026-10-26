import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { oro } from '../api/oro';
import { Button } from '../components/Button';
import { Icon } from '../components/Icon';
import { Heading, Screen } from '../components/Screen';
import { T } from '../components/Text';
import { errorText } from '../lib/errors';
import { useTheme } from '../theme/ThemeProvider';

/** A4 · Email verified: opened from the link in the email (oro://verify-email?token=…) */
export default function VerifyEmail() {
  const { token } = useLocalSearchParams<{ token?: string }>();
  const { colors } = useTheme();
  const [answer, setState] = useState<'checking' | 'done' | string>('checking');
  const state = token ? answer : 'This link is not complete. Open the link of the email again.';
  useEffect(() => {
    if (token) {
      oro.verifyEmail(token).then(() => setState('done'), (e) => setState(errorText(e)));
    }
  }, [token]);

  return (
    <Screen footer={state === 'checking' ? null : <Button label="Continue" onPress={() => router.replace('/')} />}>
      <View style={{ flex: 1, alignItems: 'center', paddingTop: 96, gap: 16 }}>
        {state === 'checking' ? <ActivityIndicator color={colors.accent} /> : (
          <>
            <View style={{ width: 88, height: 88, borderRadius: 44, alignItems: 'center', justifyContent: 'center',
              backgroundColor: state === 'done' ? colors.successSoft : colors.dangerSoft }}>
              <Icon name={state === 'done' ? 'circle-check' : 'circle-alert'} size={40} color={state === 'done' ? 'success' : 'danger'} />
            </View>
            <Heading title={state === 'done' ? 'Your email is verified' : 'This link did not work'}
              body={state === 'done' ? 'Welcome to Oro. Your Asata account is ready.' : state} />
            <T variant="caption" color="textTertiary">Opened from the link in your email</T>
          </>
        )}
      </View>
    </Screen>
  );
}
