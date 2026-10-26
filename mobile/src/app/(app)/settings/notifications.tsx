import { useQueryClient } from '@tanstack/react-query';
import { View } from 'react-native';
import { oro } from '../../../api/oro';
import { Avatar, Card, ListRow, NeedsBackend, SectionHeader } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import { InlineNav } from '../../../components/Nav';
import { Screen } from '../../../components/Screen';
import { T } from '../../../components/Text';
import { isMuted, useConversations, usePeopleOf, useTitle } from '../../../lib/queries';
import { useTheme } from '../../../theme/ThemeProvider';

/** I2 · Notifications: what they show (who and where, never the text), the muted conversations */
export default function Notifications() {
  const { colors } = useTheme();
  const client = useQueryClient();
  const conversations = useConversations();
  const people = usePeopleOf(conversations.data);
  const title = useTitle();
  const muted = (conversations.data ?? []).filter((c) => isMuted(c.mutedUntil));
  return (
    <Screen nav={<InlineNav title="Notifications" backLabel="You" />}>
      <Card>
        <ListRow icon="bell" title="Allow notifications" subtitle="Uses your phone's notification setting" chevron={false} last />
      </Card>
      <NeedsBackend what="mobile push (notifications)" />
      <SectionHeader label="What they show" />
      <View style={{ gap: 8 }}>
        {[['Amani Mwangi', 'New message', 'now'], ['Family', 'New message from Baraka', '2m ago']].map(([who, what, when]) => (
          <View key={who} style={{ flexDirection: 'row', gap: 10, padding: 12, borderRadius: 16, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border }}>
            <View style={{ width: 36, height: 36, borderRadius: 8, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' }}>
              <T weight="extrabold" color="accentContrast" size={13}>oro</T>
            </View>
            <View style={{ flex: 1 }}>
              <View style={{ flexDirection: 'row' }}>
                <T variant="bodySmall" weight="bold" style={{ flex: 1 }}>{who}</T>
                <T variant="caption" color="textTertiary">{when}</T>
              </View>
              <T variant="bodySmall" color="textSecondary">{what}</T>
            </View>
          </View>
        ))}
        <T variant="caption" color="textSecondary">Who and where, never the text of a message.</T>
      </View>
      <SectionHeader label="Muted conversations" />
      <Card>
        {muted.length === 0 ? <T color="textSecondary" style={{ padding: 16 }}>None</T> : null}
        {muted.map((c) => {
          const name = title(c, people.get);
          const until = c.mutedUntil!.startsWith('2100') ? 'Until you turn it back on'
            : `Until ${new Date(c.mutedUntil!).toLocaleString(undefined, { weekday: 'short', hour: '2-digit', minute: '2-digit' })}`;
          return (
            <View key={c.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingHorizontal: 16 }}>
              <Avatar name={name} id={c.type === 'GROUP' ? c.id : c.otherUserId ?? c.id} group={c.type === 'GROUP'} />
              <View style={{ flex: 1 }}>
                <T weight="semibold">{name}</T>
                <T variant="caption" color="textSecondary">{until}</T>
              </View>
              <Button kind="text" label="Unmute" style={{ height: 36 }} onPress={async () => {
                await oro.mute(c.id, null).catch(() => undefined);
                await client.invalidateQueries({ queryKey: ['conversations'] });
              }} />
            </View>
          );
        })}
      </Card>
    </Screen>
  );
}
