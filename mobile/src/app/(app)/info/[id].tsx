import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Modal, Pressable, ScrollView, Switch, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { oro } from '../../../api/oro';
import { Member } from '../../../api/types';
import { Backdrop } from '../../../components/Backdrop';
import { Avatar, Card, ListRow, NeedsBackend, SectionHeader } from '../../../components/Basics';
import { Dialog, DialogSpec } from '../../../components/Dialog';
import { TextField } from '../../../components/Fields';
import { Icon, IconName } from '../../../components/Icon';
import { ActionSheet, InlineNav, ModalBar, SheetAction } from '../../../components/Nav';
import { Banner, Screen } from '../../../components/Screen';
import { LoadError, Spinner } from '../../../components/States';
import { T } from '../../../components/Text';
import { useLive } from '../../../live/LiveProvider';
import { useContacts } from '../../../lib/contacts';
import { errorText } from '../../../lib/errors';
import { displayName, firstName, usePeople } from '../../../lib/people';
import { isMuted, useBlocks } from '../../../lib/queries';
import { useSession } from '../../../session/SessionProvider';
import { useTheme } from '../../../theme/ThemeProvider';

const FOREVER = '2100-01-01T00:00:00Z';

function muteLabel(until?: string): string {
  if (!isMuted(until)) {
    return 'Off';
  }
  if (until!.startsWith('2100')) {
    return 'On';
  }
  const d = new Date(until!);
  return `Until ${d.toLocaleString(undefined, { weekday: 'short', hour: '2-digit', minute: '2-digit' })}`;
}

function QuickAction({ icon, label, onPress }: { icon: IconName; label: string; onPress: () => void }) {
  const { colors } = useTheme();
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => ({ flex: 1, alignItems: 'center', gap: 6, paddingVertical: 12,
      borderRadius: 14, backgroundColor: pressed ? colors.surfaceRaised : colors.surface, borderWidth: 1, borderColor: colors.border })}>
      <Icon name={icon} size={22} color="accent" />
      <T variant="caption" weight="semibold" color="accent">{label}</T>
    </Pressable>
  );
}

/** F1 · a person (F2 mute, F3 block, F4 blocked), E3 · a group (E3b admin, E3c member actions, E3d edit, E4 add, E5 leave) */
export default function Info() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const client = useQueryClient();
  const { me } = useSession();
  const myId = me?.userId ?? '';
  const live = useLive();
  const blocks = useBlocks();
  const conversation = useQuery({ queryKey: ['conversation', id], queryFn: () => oro.conversation(id) });
  const c = conversation.data;
  const people = usePeople(c?.members.map((m) => m.userId) ?? []);
  const [muteOpen, setMuteOpen] = useState(false);
  const [member, setMember] = useState<Member | null>(null);
  const [dialog, setDialog] = useState<DialogSpec | null>(null);
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!c) {
    return (
      <Screen nav={<InlineNav title="" backLabel="Back" />}>
        {conversation.isError ? <LoadError error={conversation.error} onRetry={() => conversation.refetch()} /> : <Spinner />}
      </Screen>
    );
  }

  const group = c.type === 'GROUP';
  const other = c.members.find((m) => m.userId !== myId);
  const admin = c.role === 'OWNER' || c.role === 'ADMIN';
  const refresh = () => Promise.all([
    client.invalidateQueries({ queryKey: ['conversation', id] }),
    client.invalidateQueries({ queryKey: ['conversations'] }),
  ]);
  const act = async (work: () => Promise<unknown>) => {
    setError(null);
    try {
      await work();
      await refresh();
    } catch (e) {
      setError(errorText(e));
    }
  };
  const mute = (until: string | null) => act(() => oro.mute(id, until));
  const muteActions: SheetAction[] = [
    { label: 'For 1 hour', onPress: () => mute(new Date(Date.now() + 3600_000).toISOString()) },
    { label: 'For 8 hours', onPress: () => mute(new Date(Date.now() + 8 * 3600_000).toISOString()) },
    { label: 'For 1 week', onPress: () => mute(new Date(Date.now() + 7 * 86400_000).toISOString()) },
    { label: 'Until I turn it back on', onPress: () => mute(FOREVER) },
  ];
  const muteRow = (
    <ListRow icon={isMuted(c.mutedUntil) ? 'bell-off' : 'bell'} title="Mute" value={muteLabel(c.mutedUntil)}
      onPress={() => (isMuted(c.mutedUntil) ? mute(null) : setMuteOpen(true))} />
  );

  if (!group) {
    const person = people.get(other?.userId);
    const name = displayName(person, other?.userId);
    const first = firstName(person, other?.userId);
    const blocked = !!other && !!blocks.data?.includes(other.userId);
    const online = !!other && live.online.has(other.userId);
    return (
      <Screen nav={<InlineNav title={first} backLabel="Back" />}>
        <View style={{ alignItems: 'center', gap: 6, paddingTop: 8 }}>
          <Avatar name={name} id={other?.userId ?? id} size={96} online={online && !blocked} />
          <T size={22} weight="extrabold">{name}</T>
          <T variant="bodySmall" color="textSecondary">{person ? `@${person.username}` : ''}{online && !blocked ? ' · online' : ''}</T>
        </View>
        {error ? <Banner text={error} /> : null}
        {blocked ? (
          <Banner kind="warning" text={`You blocked ${first}. They can't message you or add you to groups, and they don't see when you're online.`} />
        ) : (
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <QuickAction icon="message-circle" label="Message" onPress={() => router.back()} />
            <QuickAction icon={isMuted(c.mutedUntil) ? 'bell-off' : 'bell'} label="Mute" onPress={() => setMuteOpen(true)} />
            <QuickAction icon="search" label="Search" onPress={() => router.push({ pathname: '/search', params: { conversationId: id, title: name } })} />
          </View>
        )}
        <Card>
          {muteRow}
          <ListRow icon="search" title="Search in conversation" last onPress={() => router.push({ pathname: '/search', params: { conversationId: id, title: name } })} />
        </Card>
        <Card>
          {blocked ? (
            <ListRow icon="shield-check" title={`Unblock ${first}`} chevron={false} onPress={() => act(async () => {
              await oro.unblock(other!.userId);
              await client.invalidateQueries({ queryKey: ['blocks'] });
            })} />
          ) : (
            <ListRow icon="ban" title={`Block ${first}`} destructive chevron={false}
              subtitle="They can't message you or add you to groups; they won't see when you're online"
              onPress={() => setDialog({
                title: `Block ${first}?`, destructive: true, confirm: 'Block',
                body: "They can't message you or add you to groups, and they won't see when you're online. You can unblock them in Privacy.",
                onConfirm: () => act(async () => {
                  await oro.block(other!.userId);
                  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => undefined);
                  await client.invalidateQueries({ queryKey: ['blocks'] });
                }),
              })} />
          )}
          <ListRow icon="flag" title={`Report ${first}`} destructive chevron={false} last
            onPress={() => router.push({ pathname: '/report', params: { conversationId: id, userId: other?.userId ?? '' } })} />
        </Card>
        <ActionSheet visible={muteOpen} onClose={() => setMuteOpen(false)} title={`Mute ${name}`} actions={muteActions} />
        <Dialog spec={dialog} onClose={() => setDialog(null)} />
      </Screen>
    );
  }

  const members = c.members.slice().sort((a, b) => {
    const rank = { OWNER: 0, ADMIN: 1, MEMBER: 2 };
    return rank[a.role] - rank[b.role] || Number(a.pending) - Number(b.pending);
  });
  const memberActions = (m: Member): SheetAction[] => {
    const first = firstName(people.get(m.userId), m.userId);
    const out: SheetAction[] = [{
      label: `Message ${first}`, icon: 'message-circle', onPress: () => act(async () => {
        const direct = await oro.openDirect(m.userId);
        router.push({ pathname: '/chat/[id]', params: { id: direct.id } });
      }),
    }];
    if (admin && m.role !== 'OWNER') {
      out.push({ label: 'Remove from group', icon: 'trash-2', destructive: true, onPress: () => act(() => oro.removeMember(id, m.userId)) });
    }
    return out;
  };

  return (
    <Screen nav={<InlineNav title="Group info" backLabel="Back" right={admin ? (
      <Pressable accessibilityRole="button" onPress={() => setEditing(true)} hitSlop={8} style={{ paddingHorizontal: 8 }}>
        <T weight="semibold" color="accent">Edit</T>
      </Pressable>
    ) : undefined} />}>
      <View style={{ alignItems: 'center', gap: 6, paddingTop: 8 }}>
        <Avatar name={c.name ?? 'Group'} id={id} size={96} group />
        <T size={22} weight="extrabold" align="center">{c.name}</T>
        <T variant="bodySmall" color="textSecondary">Group · {c.members.filter((m) => !m.pending).length} members</T>
        {c.description ? <T variant="bodySmall" align="center" style={{ lineHeight: 20, paddingHorizontal: 16 }}>{c.description}</T> : null}
      </View>
      {error ? <Banner text={error} /> : null}
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <QuickAction icon={isMuted(c.mutedUntil) ? 'bell-off' : 'bell'} label="Mute" onPress={() => setMuteOpen(true)} />
        <QuickAction icon="search" label="Search" onPress={() => router.push({ pathname: '/search', params: { conversationId: id, title: c.name } })} />
        {admin ? <QuickAction icon="user-plus" label="Add" onPress={() => setAdding(true)} /> : null}
      </View>
      <Card>{muteRow}<ListRow icon="search" title="Search in conversation" last onPress={() => router.push({ pathname: '/search', params: { conversationId: id, title: c.name } })} /></Card>
      <View>
        <SectionHeader label={`${c.members.filter((m) => !m.pending).length} members`} />
        <Card>
          {admin ? <ListRow icon="user-plus" title="Add members" onPress={() => setAdding(true)} /> : null}
          {members.map((m) => {
            const person = people.get(m.userId);
            const name = displayName(person, m.userId);
            return (
              <Pressable key={m.userId} accessibilityRole="button" disabled={m.userId === myId} onPress={() => setMember(m)}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingHorizontal: 16 }}>
                <Avatar name={name} id={m.userId} online={live.online.has(m.userId)} />
                <View style={{ flex: 1 }}>
                  <T weight="semibold">{name}{m.userId === myId ? ' (You)' : ''}</T>
                  <T variant="caption" color="textSecondary">{m.pending ? 'Invited · not joined yet' : person ? `@${person.username}` : ''}</T>
                </View>
                {m.role !== 'MEMBER' ? <T variant="caption" weight="bold" color="accent">{m.role === 'OWNER' ? 'Owner' : 'Admin'}</T> : null}
              </Pressable>
            );
          })}
        </Card>
      </View>
      <Card>
        <ListRow icon="log-out" title="Leave group" destructive chevron={false} onPress={() => setDialog({
          title: `Leave ${c.name}?`, destructive: true, confirm: 'Leave',
          body: 'It disappears from your list and you stop getting its messages. An admin can add you again.',
          onConfirm: () => act(async () => {
            await oro.removeMember(id, myId);
            router.dismissAll();
          }),
        })} />
        <ListRow icon="flag" title="Report group" destructive chevron={false} last
          onPress={() => router.push({ pathname: '/report', params: { conversationId: id } })} />
      </Card>
      <ActionSheet visible={muteOpen} onClose={() => setMuteOpen(false)} title={`Mute ${c.name}`} actions={muteActions} />
      <ActionSheet visible={!!member} onClose={() => setMember(null)} actions={member ? memberActions(member) : []}
        title={member ? `${displayName(people.get(member.userId), member.userId)}${people.get(member.userId) ? ` · @${people.get(member.userId)!.username}` : ''}` : undefined} />
      <Dialog spec={dialog} onClose={() => setDialog(null)} />
      {editing ? <EditGroup id={id} name={c.name ?? ''} description={c.description ?? ''} onClose={() => setEditing(false)} onSaved={refresh} /> : null}
      {adding ? <AddMembers id={id} groupName={c.name ?? ''} present={c.members.map((m) => m.userId)} onClose={() => setAdding(false)} onAdded={refresh} /> : null}
    </Screen>
  );
}

/** E3d · Edit group: its name and description (owner and admins) */
function EditGroup({ id, name, description, onClose, onSaved }: {
  id: string; name: string; description: string; onClose: () => void; onSaved: () => Promise<unknown>;
}) {
  const insets = useSafeAreaInsets();
  const [n, setN] = useState(name);
  const [d, setD] = useState(description);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const save = async () => {
    setBusy(true);
    try {
      await oro.rename(id, n.trim(), d.trim() || undefined);
      await onSaved();
      onClose();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal visible animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <Backdrop>
        <ModalBar title="Edit group" onCancel={onClose} action="Save" onAction={save} busy={busy} disabled={!n.trim()} top={insets.top} />
        <ScrollView contentContainerStyle={{ padding: 16, gap: 16 }} keyboardShouldPersistTaps="handled">
          <View style={{ alignItems: 'center', gap: 8 }}>
            <Avatar name={n || 'Group'} id={id} size={96} group />
            <NeedsBackend what="group photos" />
          </View>
          {error ? <Banner text={error} /> : null}
          <TextField label="Group name" value={n} onChangeText={setN} maxLength={128} helper="Members see a note in the conversation when it changes." />
          <TextField label="Description" value={d} onChangeText={setD} maxLength={1024} multiline style={{ minHeight: 80 }}
            helper="Only the owner and admins can change the name and description." />
        </ScrollView>
      </Backdrop>
    </Modal>
  );
}

/** E4 · Add members: pick from your contacts (E4a), then show them the earlier messages or not (E4b) */
function AddMembers({ id, groupName, present, onClose, onAdded }: {
  id: string; groupName: string; present: string[]; onClose: () => void; onAdded: () => Promise<unknown>;
}) {
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const contacts = useContacts();
  const [picked, setPicked] = useState<string | null>(null);
  const [history, setHistory] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const person = contacts.people.get(picked ?? undefined);
  const first = firstName(person, picked ?? undefined);

  const add = async () => {
    setBusy(true);
    try {
      await oro.addMember(id, picked!, history);
      await onAdded();
      onClose();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal visible animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <Backdrop>
        {picked ? (
          <>
            <ModalBar title={`Add ${first}`} onCancel={() => setPicked(null)} cancel="Back" action="Add" onAction={add} busy={busy} top={insets.top} />
            <ScrollView contentContainerStyle={{ padding: 16, gap: 16 }}>
              <View style={{ alignItems: 'center', gap: 6 }}>
                <Avatar name={displayName(person, picked)} id={picked} size={56} />
                <T variant="headline">{displayName(person, picked)}</T>
                <T variant="bodySmall" color="textSecondary">to {groupName}</T>
              </View>
              {error ? <Banner text={error} /> : null}
              <Card>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16 }}>
                  <View style={{ flex: 1, gap: 2 }}>
                    <T weight="semibold">Show them the earlier messages?</T>
                    <T variant="caption" color="textSecondary">
                      {history ? `On: ${first} can read the whole conversation so far, including photos and files.` : `Off: ${first} sees only what's sent from now on.`}
                    </T>
                  </View>
                  <Switch value={history} onValueChange={setHistory} trackColor={{ true: colors.accent, false: colors.surfaceRaised }} thumbColor="#FFFFFF" />
                </View>
              </Card>
              <T variant="caption" color="textSecondary" style={{ lineHeight: 17 }}>
                If you&apos;re not contacts with someone, they get an invitation and join only if they accept.
              </T>
            </ScrollView>
          </>
        ) : (
          <>
            <ModalBar title="Add members" onCancel={onClose} top={insets.top} />
            <ScrollView contentContainerStyle={{ padding: 16, gap: 8 }}>
              <SectionHeader label="Your contacts" />
              <Card>
                {contacts.ids.length === 0 ? <T color="textSecondary" style={{ padding: 16 }}>Nobody yet: your contacts are the people you talk with.</T> : null}
                {contacts.ids.map((userId) => {
                  const here = present.includes(userId);
                  const p = contacts.people.get(userId);
                  return (
                    <Pressable key={userId} accessibilityRole="button" disabled={here} onPress={() => setPicked(userId)}
                      style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingHorizontal: 16, opacity: here ? 0.5 : 1 }}>
                      <Avatar name={displayName(p, userId)} id={userId} />
                      <View style={{ flex: 1 }}>
                        <T weight="semibold">{displayName(p, userId)}</T>
                        <T variant="caption" color="textSecondary">{here ? 'Already in the group' : p ? `@${p.username}` : ''}</T>
                      </View>
                    </Pressable>
                  );
                })}
              </Card>
              <T variant="caption" color="textSecondary" style={{ lineHeight: 17, paddingTop: 8 }}>
                Someone you&apos;re not contacts with gets an invitation instead, and joins only if they accept.
              </T>
            </ScrollView>
          </>
        )}
      </Backdrop>
    </Modal>
  );
}
