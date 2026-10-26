import { Platform } from 'react-native';
import { Card, ListRow, NeedsBackend, SectionHeader } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import { InlineNav } from '../../../components/Nav';
import { Screen } from '../../../components/Screen';
import { useSession } from '../../../session/SessionProvider';

/** I5 · Sessions: this phone (sign out); the others need the backend */
export default function Sessions() {
  const { signOut } = useSession();
  const device = Platform.OS === 'ios' ? 'iPhone · Oro for iOS' : Platform.OS === 'android' ? 'Android · Oro for Android' : 'Browser · Oro preview';
  return (
    <Screen nav={<InlineNav title="Sessions" backLabel="You" />}>
      <SectionHeader label="This phone" />
      <Card>
        <ListRow icon="smartphone" title={device} subtitle="Signed in now" chevron={false} />
        <ListRow icon="log-out" title="Sign out" destructive chevron={false} onPress={signOut} last />
      </Card>
      <SectionHeader label="Everywhere else" />
      <Card>
        <ListRow icon="monitor-smartphone" title="Your other sessions" subtitle="Phones, tablets and browsers signed in" chevron={false} last />
      </Card>
      <NeedsBackend what="listing sessions (exists in Umoja)" />
      <Button label="Sign out everywhere" kind="destructive" disabled />
    </Screen>
  );
}
