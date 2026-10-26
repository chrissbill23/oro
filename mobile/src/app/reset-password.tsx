import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { oro } from '../api/oro';
import { Button } from '../components/Button';
import { TextField } from '../components/Fields';
import { Icon } from '../components/Icon';
import { Banner, Heading, Screen } from '../components/Screen';
import { T } from '../components/Text';
import { errorText } from '../lib/errors';
import { useSession } from '../session/SessionProvider';
import { useTheme } from '../theme/ThemeProvider';

/** A6c · Choose a new password (oro://reset-password?token=…), then A6d · Password changed */
export default function ResetPassword() {
  const { token } = useLocalSearchParams<{ token?: string }>();
  const { state, signOut } = useSession();
  const { colors } = useTheme();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await oro.resetPassword(token ?? '', password);
      setDone(true);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <Screen footer={<Button label="Continue to Oro" onPress={async () => {
        // every session ended with the change, this one too
        if (state === 'signedIn') {
          await signOut();
        }
        router.replace('/sign-in');
      }} />}>
        <View style={{ alignItems: 'center', paddingTop: 96, gap: 16 }}>
          <View style={{ width: 88, height: 88, borderRadius: 44, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.successSoft }}>
            <Icon name="shield-check" size={40} color="success" />
          </View>
          <Heading title="Password changed" body="You are signed out everywhere else. Use your new password next time." />
        </View>
      </Screen>
    );
  }
  return (
    <Screen>
      <Heading title="Choose a new password" body="Opened from the link in your email." />
      {error ? <Banner text={error} /> : null}
      <TextField label="New password" value={password} onChangeText={setPassword} secure autoComplete="new-password"
        textContentType="newPassword" helper="10 to 128 characters" />
      <T variant="bodySmall" color="textSecondary">Saving it signs you out everywhere else.</T>
      <Button label="Save password" onPress={submit} loading={busy} disabled={!token || password.length < 10 || password.length > 128} />
    </Screen>
  );
}
