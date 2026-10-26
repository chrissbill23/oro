import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { ApiError } from '../../api/client';
import { oro } from '../../api/oro';
import { Button } from '../../components/Button';
import { TextField } from '../../components/Fields';
import { InlineNav } from '../../components/Nav';
import { Banner, Heading, Screen } from '../../components/Screen';
import { T } from '../../components/Text';
import { errorText } from '../../lib/errors';
import { useSession } from '../../session/SessionProvider';

/** A5 · Sign in, with its errors: wrong credentials (A5b), email not verified (A5c), too many attempts (A5d) */
export default function SignIn() {
  const { signIn } = useSession();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ text: string; unverified?: boolean } | null>(null);
  const [resent, setResent] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await signIn(login.trim(), password);
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        setError({ text: 'Verify your email first. We sent you a link.', unverified: true });
      } else if (e instanceof ApiError && e.status === 401) {
        setError({ text: 'Invalid email or password, or account temporarily locked.' });
      } else {
        setError({ text: errorText(e) });
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen nav={<InlineNav title="" backLabel="Welcome" />}>
      <Heading title="Welcome back" body="Sign in with your Asata account." />
      {error ? (
        <Banner text={error.text}>
          {error.unverified && login.includes('@') ? (
            <Button kind="text" label={resent ? 'Sent. Check your email' : 'Send the link again'} disabled={resent}
              style={{ alignSelf: 'flex-start', paddingHorizontal: 0, height: 32 }}
              onPress={async () => {
                await oro.resendVerification(login.trim()).catch(() => undefined);
                setResent(true);
              }} />
          ) : null}
        </Banner>
      ) : null}
      <TextField label="Email or username" value={login} onChangeText={setLogin} autoCapitalize="none" autoCorrect={false}
        autoComplete="username" textContentType="username" returnKeyType="next" />
      <TextField label="Password" value={password} onChangeText={setPassword} secure autoComplete="current-password"
        textContentType="password" returnKeyType="go" onSubmitEditing={submit} />
      <View style={{ gap: 2 }}>
        <T weight="semibold">Keep me signed in</T>
        <T variant="caption" color="textSecondary">On this phone only. Sign out from You.</T>
      </View>
      <Button label="Sign in" onPress={submit} loading={busy} disabled={!login.trim() || !password} />
      <Button kind="text" label="Forgot password?" onPress={() => router.push('/forgot-password')} />
    </Screen>
  );
}
