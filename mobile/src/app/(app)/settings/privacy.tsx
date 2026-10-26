import { router } from 'expo-router';
import { View } from 'react-native';
import { Card, ListRow, SectionHeader } from '../../../components/Basics';
import { InlineNav } from '../../../components/Nav';
import { Screen } from '../../../components/Screen';
import { T } from '../../../components/Text';
import { useBlocks } from '../../../lib/queries';

/** I3 · Privacy: who sees you online (your contacts, fixed), the blocked people, how requests work */
export default function Privacy() {
  const blocks = useBlocks();
  const count = blocks.data?.length ?? 0;
  return (
    <Screen nav={<InlineNav title="Privacy" backLabel="You" />}>
      <View>
        <SectionHeader label="Who sees you online" />
        <Card><ListRow icon="eye" title="Your contacts" chevron={false} last /></Card>
        <T variant="caption" color="textSecondary" style={{ lineHeight: 17, padding: 4, paddingTop: 8 }}>
          People you have a conversation with, accepted by both. Nobody else sees when you&apos;re online, and Oro never shows “last seen”.
        </T>
      </View>
      <Card>
        <ListRow icon="ban" title="Blocked people" value={count ? String(count) : 'None'} onPress={() => router.push('/settings/blocked')} last />
      </Card>
      <View>
        <SectionHeader label="Message requests" />
        <Card>
          <View style={{ padding: 16, gap: 6 }}>
            <T weight="semibold">How requests work</T>
            <T variant="bodySmall" color="textSecondary" style={{ lineHeight: 20 }}>
              Someone you don&apos;t know can send you one message. It waits in Requests until you accept, decline, or decline and block. A group from a stranger arrives as an invitation.
            </T>
          </View>
        </Card>
      </View>
    </Screen>
  );
}
