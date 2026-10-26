import { Share, View } from 'react-native';
import { Avatar, NeedsBackend } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import { InlineNav } from '../../../components/Nav';
import { Screen } from '../../../components/Screen';
import { T } from '../../../components/Text';
import { displayName, usePeople } from '../../../lib/people';
import { useSession } from '../../../session/SessionProvider';

/** G1b · My username: shared as text; a QR code and finding people need the backend */
export default function Username() {
  const { me } = useSession();
  const people = usePeople([me?.userId]);
  const person = people.get(me?.userId);
  return (
    <Screen nav={<InlineNav title="My username" backLabel="Back" />}>
      <View style={{ alignItems: 'center', gap: 8, paddingTop: 16 }}>
        <Avatar name={displayName(person, me?.userId)} id={me?.userId ?? 'me'} size={96} />
        <T size={22} weight="extrabold">{displayName(person, me?.userId)}</T>
        <T variant="headline" color="accent">{person?.username}</T>
      </View>
      <T color="textSecondary" align="center" style={{ lineHeight: 22 }}>
        People who scan this or type your username can send you a request. They see your name, never when you&apos;re online.
      </T>
      <NeedsBackend what="finding a person (username, QR code)" />
      <Button label="Share my username" icon="share" disabled={!person}
        onPress={() => void Share.share({ message: `Write to me on Oro: ${person?.username}` })} />
    </Screen>
  );
}
