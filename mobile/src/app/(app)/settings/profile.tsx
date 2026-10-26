import { View } from 'react-native';
import { Avatar, Card, ListRow, NeedsBackend } from '../../../components/Basics';
import { InlineNav } from '../../../components/Nav';
import { Screen } from '../../../components/Screen';
import { T } from '../../../components/Text';
import { displayName, usePeople } from '../../../lib/people';
import { useSession } from '../../../session/SessionProvider';

/** I1 · Profile: the name and username of the Asata account (changed in the account, not here) */
export default function Profile() {
  const { me } = useSession();
  const people = usePeople([me?.userId]);
  const person = people.get(me?.userId);
  const name = displayName(person, me?.userId);
  return (
    <Screen nav={<InlineNav title="Profile" backLabel="You" />}>
      <View style={{ alignItems: 'center', gap: 8 }}>
        <Avatar name={name} id={me?.userId ?? 'me'} size={96} />
        <T weight="semibold" color="textTertiary">Add a photo</T>
        <NeedsBackend what="profile photos" />
      </View>
      <Card>
        <ListRow title="Name" value={name} chevron={false} />
        <ListRow title="Username" value={person ? `@${person.username}` : ''} chevron={false} last />
      </Card>
      <T variant="caption" color="textSecondary" style={{ lineHeight: 17, paddingHorizontal: 4 }}>
        Shared with the people you talk to. Your name and username come from your Asata account, the same in Umoja and Mokili.
      </T>
    </Screen>
  );
}
