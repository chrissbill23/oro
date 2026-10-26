import { useQueryClient } from '@tanstack/react-query';
import { View } from 'react-native';
import { oro } from '../../../api/oro';
import { Avatar, Card } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import { InlineNav } from '../../../components/Nav';
import { Screen } from '../../../components/Screen';
import { Spinner } from '../../../components/States';
import { T } from '../../../components/Text';
import { displayName, usePeople } from '../../../lib/people';
import { useBlocks } from '../../../lib/queries';

/** I3b · Blocked people, to unblock */
export default function Blocked() {
  const client = useQueryClient();
  const blocks = useBlocks();
  const people = usePeople(blocks.data ?? []);
  return (
    <Screen nav={<InlineNav title="Blocked people" backLabel="Privacy" />}>
      {blocks.isPending ? <Spinner /> : (
        <Card>
          {(blocks.data ?? []).length === 0 ? <T color="textSecondary" style={{ padding: 16 }}>Nobody</T> : null}
          {(blocks.data ?? []).map((userId) => {
            const p = people.get(userId);
            return (
              <View key={userId} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingHorizontal: 16 }}>
                <Avatar name={displayName(p, userId)} id={userId} />
                <View style={{ flex: 1 }}>
                  <T weight="semibold">{displayName(p, userId)}</T>
                  {p ? <T variant="caption" color="textSecondary">@{p.username}</T> : null}
                </View>
                <Button kind="secondary" label="Unblock" style={{ height: 36 }} onPress={async () => {
                  await oro.unblock(userId).catch(() => undefined);
                  await client.invalidateQueries({ queryKey: ['blocks'] });
                }} />
              </View>
            );
          })}
        </Card>
      )}
      <T variant="caption" color="textSecondary" style={{ lineHeight: 17, paddingHorizontal: 4 }}>
        People you block can&apos;t message you or add you to groups, and don&apos;t see when you&apos;re online.
      </T>
    </Screen>
  );
}
