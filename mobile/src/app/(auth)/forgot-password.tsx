import { router } from 'expo-router';
import { useState } from 'react';
import { oro } from '../../api/oro';
import { Button } from '../../components/Button';
import { TextField } from '../../components/Fields';
import { InlineNav } from '../../components/Nav';
import { Banner, Heading, Screen } from '../../components/Screen';
import { errorText } from '../../lib/errors';

/** A6a · Forgot password: the same answer whatever the address (A6b) */
export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await oro.forgotPassword(email.trim());
      router.replace({ pathname: '/check-email', params: { email: email.trim(), kind: 'reset' } });
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Screen nav={<InlineNav title="" backLabel="Sign in" />}>
      <Heading title="Reset your password" body="Enter the email of your Asata account. We'll send you a link to choose a new password." />
      {error ? <Banner text={error} /> : null}
      <TextField label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address"
        autoComplete="email" textContentType="emailAddress" onSubmitEditing={submit} />
      <Button label="Send the link" onPress={submit} loading={busy} disabled={!email.includes('@')} />
    </Screen>
  );
}
