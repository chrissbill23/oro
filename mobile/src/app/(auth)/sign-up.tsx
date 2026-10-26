import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { ApiError } from '../../api/client';
import { oro } from '../../api/oro';
import { Button } from '../../components/Button';
import { TextField } from '../../components/Fields';
import { InlineNav } from '../../components/Nav';
import { Banner, Heading, Screen } from '../../components/Screen';
import { errorText } from '../../lib/errors';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME = /^[A-Za-z0-9._-]{3,64}$/;

/** A2 · Sign up (A2b: its errors); then A3, check your email */
export default function SignUp() {
  const [givenName, setGivenName] = useState('');
  const [surname, setSurname] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [tried, setTried] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [taken, setTaken] = useState<string | null>(null);

  const emailError = tried && !EMAIL.test(email.trim()) ? 'Enter a valid email address.' : undefined;
  const usernameError = taken === username ? 'This username is taken. Try another one.'
    : tried && !USERNAME.test(username) ? '3 to 64 letters, digits, dots, dashes or underscores.' : undefined;
  const passwordError = tried && (password.length < 10 || password.length > 128) ? '10 to 128 characters' : undefined;

  const submit = async () => {
    setTried(true);
    setError(null);
    if (!EMAIL.test(email.trim()) || !USERNAME.test(username) || password.length < 10 || password.length > 128 || !givenName.trim()) {
      return;
    }
    setBusy(true);
    try {
      await oro.signUp({ email: email.trim(), username, password, givenName: givenName.trim(), surname: surname.trim() });
      router.replace({ pathname: '/check-email', params: { email: email.trim(), kind: 'verify' } });
    } catch (e) {
      if (e instanceof ApiError && e.status === 409 && /username/i.test(e.message)) {
        setTaken(username);
      } else {
        setError(errorText(e));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen nav={<InlineNav title="" backLabel="Welcome" />}>
      <Heading title="Create your account" body="One Asata account for Oro, Umoja and Mokili." />
      {error ? <Banner text={error} /> : null}
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <TextField label="Given name" value={givenName} onChangeText={setGivenName} autoComplete="given-name"
            textContentType="givenName" error={tried && !givenName.trim() ? 'Your given name' : undefined} />
        </View>
        <View style={{ flex: 1 }}>
          <TextField label="Surname" value={surname} onChangeText={setSurname} autoComplete="family-name" textContentType="familyName" />
        </View>
      </View>
      <TextField label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false}
        autoComplete="username-new" textContentType="username" error={usernameError} />
      <TextField label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address"
        autoComplete="email" textContentType="emailAddress" error={emailError} />
      <TextField label="Password" value={password} onChangeText={setPassword} secure autoComplete="new-password"
        textContentType="newPassword" error={passwordError}
        helper={`10 to 128 characters${password ? ` · ${password.length} so far` : ''}`} />
      <Button label="Create account" onPress={submit} loading={busy} />
      <Button kind="text" label="I have an account · Sign in" onPress={() => router.replace('/sign-in')} />
    </Screen>
  );
}
