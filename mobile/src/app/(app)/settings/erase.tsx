import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { ApiError } from '../../../api/client';
import { oro } from '../../../api/oro';
import { Button } from '../../../components/Button';
import { TextField } from '../../../components/Fields';
import { Icon } from '../../../components/Icon';
import { InlineNav } from '../../../components/Nav';
import { Banner, Heading, Screen } from '../../../components/Screen';
import { T } from '../../../components/Text';
import { errorText } from '../../../lib/errors';
import { useSession } from '../../../session/SessionProvider';
import { useTheme } from '../../../theme/ThemeProvider';

/** I4c · Erase, confirmed with the password; then signed out of Oro (the Asata account stays) */
export default function Erase() {
  const { colors } = useTheme();
  const { signOut } = useSession();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const erase = async () => {
    setBusy(true);
    setError(null);
    try {
      await oro.erase(password);
      await signOut();
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? 'Wrong password.' : errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Screen nav={<InlineNav title="Erase my data" backLabel="Your data" />}
      footer={<>
        <Button label="Erase my data" kind="destructive" loading={busy} disabled={!password} onPress={erase} />
        <Button label="Keep my data" kind="secondary" onPress={() => router.back()} />
      </>}>
      <View style={{ width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.dangerSoft }}>
        <Icon name="trash-2" size={28} color="danger" />
      </View>
      <Heading title="This is final" body="Your messages become “Message deleted”, your reactions and files are erased. Your Asata account stays for Umoja and Mokili." />
      <T weight="semibold">It cannot be undone. Export your data first if you want a copy.</T>
      {error ? <Banner text={error} /> : null}
      <TextField label="Your password" value={password} onChangeText={setPassword} secure autoComplete="current-password" textContentType="password" />
    </Screen>
  );
}
