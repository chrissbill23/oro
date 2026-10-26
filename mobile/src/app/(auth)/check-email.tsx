import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { oro } from '../../api/oro';
import { Button } from '../../components/Button';
import { Icon } from '../../components/Icon';
import { InlineNav } from '../../components/Nav';
import { Heading, Screen } from '../../components/Screen';
import { T } from '../../components/Text';
import { useTheme } from '../../theme/ThemeProvider';

const WAIT = 60;

/** A3 · Check your email (a verification) and A6b (a reset): send it again after a minute */
export default function CheckEmail() {
  const { email = '', kind = 'verify' } = useLocalSearchParams<{ email: string; kind: 'verify' | 'reset' }>();
  const { colors } = useTheme();
  const [left, setLeft] = useState(WAIT);
  useEffect(() => {
    if (left <= 0) {
      return;
    }
    const t = setTimeout(() => setLeft((l) => l - 1), 1000);
    return () => clearTimeout(t);
  }, [left]);

  const again = async () => {
    setLeft(WAIT);
    await (kind === 'reset' ? oro.forgotPassword(email) : oro.resendVerification(email)).catch(() => undefined);
  };
  const body = kind === 'reset'
    ? `If ${email} has an Asata account, a link to reset your password is on its way.`
    : `We sent a link to ${email}. Open it on this phone to continue.`;

  return (
    <Screen nav={<InlineNav title="" backLabel="Back" />}>
      <View style={{ width: 72, height: 72, borderRadius: 36, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.accentSoft, marginTop: 16 }}>
        <Icon name="mail-check" size={32} color="accent" />
      </View>
      <Heading title="Check your email" body={body} />
      <T variant="bodySmall" color="textSecondary">Can&apos;t find it? Look in your spam folder, or send it again.</T>
      <Button kind="secondary" disabled={left > 0} onPress={again}
        label={left > 0 ? `Send it again in 0:${String(left).padStart(2, '0')}` : 'Send it again'} />
      <Button kind="text" label="Wrong address?" onPress={() => router.back()} />
    </Screen>
  );
}
