import { useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { oro } from '../../../api/oro';
import { Avatar } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import { MessageBubble } from '../../../components/Chat';
import { Dialog, DialogSpec } from '../../../components/Dialog';
import { InlineNav } from '../../../components/Nav';
import { Banner, Screen } from '../../../components/Screen';
import { Spinner } from '../../../components/States';
import { T } from '../../../components/Text';
import { errorText } from '../../../lib/errors';
import { displayName, firstName, usePeople } from '../../../lib/people';
import { useRequests } from '../../../lib/queries';
import { useTheme } from '../../../theme/ThemeProvider';

/** C2 · a message request opened, C3 · a group invitation: accept, decline, decline and block, report */
export default function RequestScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useTheme();
  const client = useQueryClient();
  const requests = useRequests();
  const summary = requests.data?.find((r) => r.id === id);
  const messages = useQuery({ queryKey: ['request-messages', id], queryFn: () => oro.newest(id, 20) });
  const group = summary?.type === 'GROUP';
  const asker = group ? summary?.invitedByUserId : summary?.otherUserId;
  const people = usePeople([asker, ...(messages.data?.messages.map((m) => m.senderUserId) ?? [])]);
  const name = displayName(people.get(asker), asker);
  const first = firstName(people.get(asker), asker);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<DialogSpec | null>(null);

  const answer = async (what: 'accept' | 'decline' | 'block') => {
    setBusy(what);
    setError(null);
    try {
      if (what === 'accept') {
        await oro.accept(id);
      } else {
        await oro.decline(id, what === 'block');
      }
      await Promise.all([
        client.invalidateQueries({ queryKey: ['requests'] }),
        client.invalidateQueries({ queryKey: ['conversations'] }),
        what === 'block' ? client.invalidateQueries({ queryKey: ['blocks'] }) : Promise.resolve(),
      ]);
      if (what === 'accept') {
        router.replace({ pathname: '/chat/[id]', params: { id } });
      } else {
        router.back();
      }
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(null);
    }
  };

  const shown = (messages.data?.messages ?? []).filter((m) => m.type !== 'SYSTEM').slice().reverse();
  return (
    <Screen nav={<InlineNav title={group ? 'Group invitation' : 'Message request'} backLabel="Requests" />}
      footer={
        <>
          {error ? <Banner text={error} /> : null}
          <Button label={group ? `Join ${summary?.name ?? 'the group'}` : 'Accept'} loading={busy === 'accept'} onPress={() => answer('accept')} />
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button style={{ flex: 1 }} kind="secondary" label="Decline" loading={busy === 'decline'} onPress={() => answer('decline')} />
            {!group ? (
              <Button style={{ flex: 1 }} kind="destructive" label="Decline and block" loading={busy === 'block'}
                onPress={() => setDialog({ title: `Block ${first}?`, body: "They can't message you or add you to groups, and they won't see when you're online. You can unblock them in Privacy.", confirm: 'Decline and block', destructive: true, onConfirm: () => answer('block') })} />
            ) : null}
          </View>
          <Button kind="text" label="Report" onPress={() => router.push({ pathname: '/report', params: { conversationId: id, messageIds: shown.map((m) => m.id).join(','), userId: asker ?? '' } })} />
        </>
      }>
      <View style={{ alignItems: 'center', gap: 8, paddingTop: 8 }}>
        <Avatar name={group ? summary?.name ?? 'Group' : name} id={group ? id : asker ?? id} size={96} group={group} />
        <T variant="headline" size={22} weight="extrabold">{group ? summary?.name : name}</T>
        <T variant="bodySmall" color="textSecondary">
          {group ? `Group · ${summary?.memberCount ?? ''} members` : `${people.get(asker) ? `@${people.get(asker)!.username} · ` : ''}not in your contacts`}
        </T>
        {group ? <T variant="bodySmall" weight="semibold">{name} invited you</T> : null}
      </View>
      {messages.isPending ? <Spinner /> : shown.map((m, i) => (
        <MessageBubble key={m.id} message={m} mine={false} first={i === 0} sender={group ? displayName(people.get(m.senderUserId), m.senderUserId) : undefined} />
      ))}
      <View style={{ padding: 12, borderRadius: 12, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border }}>
        <T variant="bodySmall" color="textSecondary" style={{ lineHeight: 20 }}>
          {group
            ? 'If you accept, you join the group: its members see your messages there. Nothing happens if you decline.'
            : `${first} can send you one message until you accept, and doesn't see when you're online.`}
        </T>
      </View>
      <Dialog spec={dialog} onClose={() => setDialog(null)} />
    </Screen>
  );
}
