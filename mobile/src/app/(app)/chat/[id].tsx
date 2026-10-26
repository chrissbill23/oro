import { FlashList, FlashListRef } from '@shopify/flash-list';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import * as Haptics from 'expo-haptics';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Keyboard, KeyboardAvoidingView, Linking, Platform, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ApiError } from '../../../api/client';
import { oro } from '../../../api/oro';
import { Conversation, ConversationSummary } from '../../../api/types';
import { Backdrop } from '../../../components/Backdrop';
import { EmptyState } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import {
  Anchor, ChatMessage, Composer, DateSeparator, Delivery, JumpToNew, MessageBubble, SeenLabel, SystemMessage, TypingIndicator,
  UnreadSeparator,
} from '../../../components/Chat';
import { Dialog, DialogSpec } from '../../../components/Dialog';
import { Icon } from '../../../components/Icon';
import { MenuAction, MessageMenu } from '../../../components/MessageMenu';
import { ActionSheet, ConversationHeader } from '../../../components/Nav';
import { Spinner } from '../../../components/States';
import { T } from '../../../components/Text';
import { useLive } from '../../../live/LiveProvider';
import { MAX_FILE_BYTES } from '../../../lib/errors';
import { openAttachment, pickDocument, PickResult, pickPhoto, takePhoto } from '../../../lib/files';
import { dayLabel, fileSize, isSameDay, systemText } from '../../../lib/format';
import { SendProblem, useMessages } from '../../../lib/messages';
import { displayName, firstName, usePeople } from '../../../lib/people';
import { useBlocks } from '../../../lib/queries';
import { useSession } from '../../../session/SessionProvider';
import { useTheme } from '../../../theme/ThemeProvider';

const TYPING_EVERY = 3000;

type Row =
  | { kind: 'message'; key: string; message: ChatMessage; first: boolean }
  | { kind: 'date'; key: string; label: string }
  | { kind: 'unread'; key: string }
  | { kind: 'seen'; key: string; text: string }
  | { kind: 'typing'; key: string; who?: string };

/** D1 · a direct conversation, E1 · a group: the messages newest at the bottom, the composer, and their states */
export default function ConversationScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const client = useQueryClient();
  const { me } = useSession();
  const myId = me?.userId ?? '';
  const live = useLive();
  const blocks = useBlocks();
  const conversation = useQuery({ queryKey: ['conversation', id], queryFn: () => oro.conversation(id) });
  const chat = useMessages(id);
  const c = conversation.data;
  const group = c?.type === 'GROUP';
  const other = c?.members.find((m) => m.userId !== myId);
  const mine = c?.members.find((m) => m.userId === myId);
  const people = usePeople([...(c?.members.map((m) => m.userId) ?? []), ...chat.messages.map((m) => m.senderUserId)]);

  const [text, setText] = useState('');
  const [mentions, setMentions] = useState<Map<string, string>>(new Map());
  const [editing, setEditing] = useState<ChatMessage | null>(null);
  const [selected, setSelected] = useState<{ message: ChatMessage; at: Anchor } | null>(null);
  const [attachOpen, setAttachOpen] = useState(false);
  const [dialog, setDialog] = useState<DialogSpec | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [scrolledUp, setScrolledUp] = useState(false);
  const [arrived, setArrived] = useState(0);
  // with the keyboard up, the composer sits on it: no room kept for the home indicator
  const [keyboardUp, setKeyboardUp] = useState(false);
  // the newest message, once the list has drawn what was just added (or the space the keyboard took)
  const toLatest = () => setTimeout(() => list.current?.scrollToEnd({ animated: true }), 80);
  const scrolledUpRef = useRef(false);
  useEffect(() => {
    const ios = Platform.OS === 'ios';
    const shown = Keyboard.addListener(ios ? 'keyboardWillShow' : 'keyboardDidShow', () => {
      setKeyboardUp(true);
      if (!scrolledUpRef.current) {
        setTimeout(() => list.current?.scrollToEnd({ animated: true }), 250);
      }
    });
    const hidden = Keyboard.addListener(ios ? 'keyboardWillHide' : 'keyboardDidHide', () => setKeyboardUp(false));
    return () => {
      shown.remove();
      hidden.remove();
    };
  }, []);
  const list = useRef<FlashListRef<Row>>(null);
  const lastTyping = useRef(0);
  const readSent = useRef(0);
  // where the new messages start: what was not read when the conversation opened
  const [unreadFrom, setUnreadFrom] = useState<number | null>(null);
  if (mine && unreadFrom === null) {
    setUnreadFrom(mine.lastReadSeq);
  }

  // read: the newest message, seen when it came at the bottom
  const newestSeq = chat.messages.find((m) => !m.local)?.seq ?? 0;
  useEffect(() => {
    if (!c || scrolledUp || newestSeq <= readSent.current || newestSeq <= (mine?.lastReadSeq ?? 0)) {
      return;
    }
    readSent.current = newestSeq;
    oro.read(id, newestSeq).catch(() => {
      readSent.current = 0;
    });
    client.setQueryData<ConversationSummary[]>(['conversations'], (all) => all?.map((x) => (x.id === id ? { ...x, unreadCount: 0 } : x)));
  }, [c, client, id, mine?.lastReadSeq, newestSeq, scrolledUp]);

  // the stream: who read what (Seen), the conversation changed (members, name), new ones while reading higher
  useEffect(
    () =>
      live.subscribe((event) => {
        if (event.type === 'read' && event.conversationId === id) {
          client.setQueryData<Conversation>(['conversation', id], (cur) =>
            cur && { ...cur, members: cur.members.map((m) => (m.userId === event.userId ? { ...m, lastReadSeq: Math.max(m.lastReadSeq, event.seq) } : m)) });
        } else if (event.type === 'conversation' && event.conversationId === id) {
          void conversation.refetch();
        } else if (event.type === 'message' && event.conversationId === id && event.change === 'created') {
          setArrived((n) => n + 1);
        } else if (event.type === 'hello') {
          void conversation.refetch();
          // J2: what did not go while offline goes now (the same client id: never twice)
          chat.messages.filter((m) => m.local === 'failed' && !m.pendingFile).forEach((m) => void chat.send(m.body ?? '', [], myId, m));
        }
      }),
    [chat, client, conversation, id, live, myId],
  );

  const problem = (p: SendProblem | null, name?: string) => {
    if (p === 'rateLimited') {
      setNotice("You're sending a lot — wait a moment");
      setTimeout(() => setNotice(null), 4000);
    } else if (p === 'oneMessage') {
      setNotice(`You can send one message until ${name ?? 'they accept'}.`);
    } else if (p === 'filesToday') {
      setDialog({ icon: 'timer', title: "You've sent 200 files today", body: "That's the daily limit. You can send more files tomorrow; messages still work.", cancel: 'OK' });
    } else if (p === 'quota') {
      setDialog({ icon: 'database', title: 'No space left for your files', body: 'Your files take all the space Oro keeps for you. Messages still work.', cancel: 'OK' });
    }
  };

  const otherName = other ? displayName(people.get(other.userId), other.userId) : '';
  const otherFirst = other ? firstName(people.get(other.userId), other.userId) : '';
  const blocked = !group && !!other && !!blocks.data?.includes(other.userId);
  const waitingAccept = !group && !!other?.pending;
  const sentOne = chat.messages.some((m) => m.senderUserId === myId && !m.local);
  const locked = blocked ? `You blocked ${otherFirst}. Unblock them to write.`
    : waitingAccept && sentOne ? `You can send one message until ${otherFirst} accepts.`
      : other?.userId.startsWith('erased-') && !group ? 'This account was deleted.' : undefined;

  // mentions (E2): the members whose username starts with what follows the last @
  const mentionQuery = group ? /(?:^|\s)@([\w.-]*)$/.exec(text)?.[1] : undefined;
  const suggestions = mentionQuery === undefined || !c ? [] : c.members
    .filter((m) => m.userId !== myId && !m.pending)
    .map((m) => ({ userId: m.userId, person: people.get(m.userId) }))
    .filter(({ person }) => person && (person.username.toLowerCase().startsWith(mentionQuery.toLowerCase())
      || displayName(person).toLowerCase().includes(mentionQuery.toLowerCase())))
    .slice(0, 5);

  const typed = (value: string) => {
    setText(value);
    if (!editing && value && Date.now() - lastTyping.current > TYPING_EVERY) {
      lastTyping.current = Date.now();
      oro.typing(id).catch(() => undefined);
    }
  };

  const submit = async () => {
    const body = text.trim();
    if (!body) {
      return;
    }
    if (editing) {
      const target = editing;
      setEditing(null);
      setText('');
      try {
        chat.accept([await oro.edit(target.id, body)]);
      } catch (e) {
        setNotice(e instanceof ApiError ? e.message : 'The message was not edited.');
      }
      return;
    }
    setText('');
    const ids = [...mentions.entries()].filter(([username]) => body.includes(`@${username}`)).map(([, userId]) => userId);
    setMentions(new Map());
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => undefined);
    const sent = chat.send(body, ids, myId);
    toLatest();
    problem(await sent, `${otherFirst} accepts`);
  };

  const attach = async (pick: () => Promise<PickResult>) => {
    const result = await pick().catch(() => null);
    if (!result) {
      return;
    }
    if ('denied' in result) {
      setDialog({
        icon: 'image', title: "Oro can't see your photos", body: "You chose not to share your photos with Oro. You can change it in your phone's Settings.",
        steps: ['Tap Oro, then Photos', 'Choose Full Access or Selected Photos'], confirm: 'Open Settings', cancel: 'Not now',
        onConfirm: () => void Linking.openSettings(),
      });
      return;
    }
    if (result.picked.size > MAX_FILE_BYTES) {
      setDialog({ icon: 'file-x', title: 'This file is too large', body: `Files can be up to 100 MB. “${result.picked.name}” is ${fileSize(result.picked.size)}.`, cancel: 'OK' });
      return;
    }
    const sent = chat.sendFile(result.picked, myId);
    toLatest();
    problem(await sent);
  };

  const react = async (m: ChatMessage, emoji: string) => {
    void Haptics.selectionAsync().catch(() => undefined);
    const had = m.reactions.find((r) => r.emoji === emoji)?.userIds.includes(myId);
    try {
      await (had ? oro.unreact(m.id, emoji) : oro.react(m.id, emoji));
      chat.accept([await oro.message(m.id)]);
    } catch {
      // the stream brings the truth
    }
  };

  const retry = (m: ChatMessage) => {
    if (m.pendingFile) {
      void chat.sendFile(m.pendingFile, myId, m).then((p) => problem(p));
    } else {
      void chat.send(m.body ?? '', [], myId, m).then((p) => problem(p, `${otherFirst} accepts`));
    }
  };

  const actions = (m: ChatMessage): MenuAction[] => {
    const own = m.senderUserId === myId;
    const out: MenuAction[] = [];
    if (m.local === 'failed') {
      out.push({ label: 'Try again', icon: 'arrow-up', onPress: () => retry(m) }, { label: 'Delete', icon: 'trash-2', destructive: true, onPress: () => chat.discard(m.id) });
      return out;
    }
    if (m.body) {
      out.push({ label: 'Copy', icon: 'copy', onPress: () => void Clipboard.setStringAsync(m.body ?? '') });
    }
    if (own && m.type === 'TEXT') {
      out.push({ label: 'Edit', icon: 'pencil', onPress: () => {
        setEditing(m);
        setText(m.body ?? '');
      } });
    }
    if (!own) {
      out.push({ label: 'Report', icon: 'flag', onPress: () => router.push({ pathname: '/report', params: { conversationId: id, messageIds: m.id, userId: m.senderUserId ?? '' } }) });
    }
    if (own) {
      out.push({ label: 'Delete', icon: 'trash-2', destructive: true, onPress: () => setDialog({
        title: 'Delete for everyone?', body: 'Its content is erased for everyone. “Message deleted” stays in its place.',
        confirm: 'Delete', destructive: true, onConfirm: async () => {
          try {
            await oro.remove(m.id);
            chat.accept([{ ...m, deleted: true, body: undefined, attachments: [], reactions: [] }]);
          } catch (e) {
            setNotice(e instanceof ApiError ? e.message : 'The message was not deleted.');
          }
        },
      }) });
    }
    return out;
  };

  // the rows, built newest first (typing, messages with their days, where the new ones start, Seen), shown oldest first
  const rows: Row[] = (() => {
    const out: Row[] = [];
    const typing = live.typing(id).filter((u) => u !== myId);
    if (typing.length) {
      out.push({ kind: 'typing', key: 'typing', who: group ? firstName(people.get(typing[0]), typing[0]) : undefined });
    }
    const msgs = chat.messages;
    const myLast = msgs.find((m) => m.senderUserId === myId && !m.local && !m.deleted && m.type !== 'SYSTEM');
    const othersRead = (c?.members ?? []).filter((m) => m.userId !== myId && !m.pending);
    let unreadPlaced = false;
    msgs.forEach((m, i) => {
      const older = msgs[i + 1];
      if (myLast && m.id === myLast.id) {
        const seenBy = othersRead.filter((x) => x.lastReadSeq >= m.seq).length;
        if (seenBy > 0) {
          out.push({ kind: 'seen', key: `seen-${m.id}`, text: group ? `Seen by ${seenBy}` : 'Seen' });
        }
      }
      const first = !older || older.senderUserId !== m.senderUserId || older.type === 'SYSTEM' || !isSameDay(older.createdAt, m.createdAt);
      out.push({ kind: 'message', key: m.id, message: m, first });
      const from = unreadFrom;
      if (!unreadPlaced && from !== null && !m.local && m.seq > from && m.senderUserId !== myId && (!older || older.seq <= from)) {
        out.push({ kind: 'unread', key: 'unread' });
        unreadPlaced = true;
      }
      if (!older || !isSameDay(older.createdAt, m.createdAt)) {
        out.push({ kind: 'date', key: `day-${m.createdAt.slice(0, 10)}-${m.id}`, label: dayLabel(m.createdAt) });
      }
    });
    return out.reverse();
  })();

  const delivery = (m: ChatMessage): Delivery | undefined => {
    if (m.local) return m.local;
    if (m.senderUserId !== myId) return undefined;
    return (c?.members ?? []).some((x) => x.userId !== myId && x.lastReadSeq >= m.seq) ? 'seen' : 'sent';
  };

  // E5b: removed (or the conversation gone) while open
  const gone = conversation.error instanceof ApiError && (conversation.error.status === 403 || conversation.error.status === 404);
  if (gone) {
    return (
      <Backdrop>
        <View style={{ flex: 1, justifyContent: 'center', paddingBottom: insets.bottom }}>
          <EmptyState icon="users-round" title="You're no longer in this conversation" body="It will disappear from your list."
            action={<Button label="Back to chats" onPress={() => router.replace('/')} style={{ alignSelf: 'stretch', marginTop: 8 }} />} />
        </View>
      </Backdrop>
    );
  }

  const title = c ? (group ? c.name ?? 'Group' : otherName) : '';
  const subtitle = !c ? undefined : group ? `${c.members.filter((m) => !m.pending).length} members`
    : waitingAccept ? "Hasn't accepted yet" : other && live.online.has(other.userId) ? 'online' : undefined;

  return (
    <Backdrop>
      <ConversationHeader name={title} id={group ? id : other?.userId ?? id} group={group} subtitle={subtitle}
        online={!group && !!other && live.online.has(other.userId)}
        onInfo={() => router.push({ pathname: '/info/[id]', params: { id } })} />
      {live.status === 'offline' ? (
        <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center', paddingVertical: 8, paddingHorizontal: 16, backgroundColor: colors.warningSoft }}>
          <Icon name="wifi-off" size={16} color="warning" />
          <T variant="caption" weight="semibold" color="warning">Offline · messages send when you&apos;re back</T>
        </View>
      ) : live.status === 'updating' ? (
        <View style={{ alignItems: 'center', paddingVertical: 6 }}><T variant="caption" weight="semibold" color="textSecondary">Updating…</T></View>
      ) : null}
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={{ flex: 1 }}>
          {chat.loading && chat.messages.length === 0 ? <Spinner /> : chat.error && chat.messages.length === 0 ? (
            <EmptyState icon="circle-alert" title="Something went wrong" body="We couldn't load the messages. Check your connection and try again."
              action={<Button label="Try again" kind="secondary" onPress={chat.reload} style={{ alignSelf: 'stretch', marginTop: 8 }} />} />
          ) : (
            <FlashList
              ref={list}
              // a chat the FlashList 2 way: oldest first, opened at the bottom, older pages added above without a
              // jump, and the newest followed while one is near the bottom
              maintainVisibleContentPosition={{ startRenderingFromBottom: true, autoscrollToBottomThreshold: 0.2 }}
              data={rows}
              keyExtractor={(r) => r.key}
              getItemType={(r) => r.kind}
              onStartReached={chat.loadOlder}
              onStartReachedThreshold={0.5}
              onScroll={(e) => {
                const { contentOffset, contentSize, layoutMeasurement } = e.nativeEvent;
                const up = contentSize.height - layoutMeasurement.height - contentOffset.y > 240;
                scrolledUpRef.current = up;
                setScrolledUp(up);
                if (!up) {
                  setArrived(0);
                }
              }}
              scrollEventThrottle={100}
              keyboardDismissMode="interactive"
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={{ paddingVertical: 8 }}
              ListHeaderComponent={waitingAccept && !group ? (
                <View style={{ margin: 16, padding: 14, borderRadius: 14, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, gap: 4 }}>
                  <T variant="bodySmall" weight="bold">You&apos;re not contacts yet</T>
                  <T variant="bodySmall" color="textSecondary" style={{ lineHeight: 20 }}>
                    Your message arrives as a request: {otherFirst} can accept, decline or block.
                  </T>
                </View>
              ) : null}
              renderItem={({ item: r }) => {
                switch (r.kind) {
                  case 'date': return <DateSeparator label={r.label} />;
                  case 'unread': return <UnreadSeparator />;
                  case 'seen': return <SeenLabel text={r.text} />;
                  case 'typing': return <TypingIndicator who={r.who} />;
                  case 'message': {
                    const m = r.message;
                    if (m.type === 'SYSTEM') {
                      return <SystemMessage text={systemText(m, people.get, myId)} />;
                    }
                    const own = m.senderUserId === myId;
                    return (
                      <MessageBubble message={m} mine={own} first={r.first} myId={myId} delivery={delivery(m)}
                        sender={group && !own ? displayName(people.get(m.senderUserId), m.senderUserId) : undefined}
                        onLongPress={m.deleted || m.local === 'sending' ? undefined : (at) => setSelected({ message: m, at })}
                        onRetry={() => retry(m)} onReact={(emoji) => react(m, emoji)}
                        onOpenFile={(a) => openAttachment(a).catch(() => setNotice('The file could not be opened.'))} />
                    );
                  }
                }
              }}
            />
          )}
          {scrolledUp ? <JumpToNew count={arrived} onPress={() => list.current?.scrollToEnd({ animated: true })} /> : null}
        </View>
        {suggestions.length ? (
          <View accessibilityRole="menu" style={{ backgroundColor: colors.background, borderTopWidth: 1, borderTopColor: colors.border }}>
            <T variant="caption" weight="bold" color="textSecondary" style={{ paddingHorizontal: 16, paddingTop: 8 }}>Members</T>
            {suggestions.map(({ userId, person }) => (
              <Pressable key={userId} accessibilityRole="menuitem" onPress={() => {
                setText(text.replace(/@([\w.-]*)$/, `@${person!.username} `));
                setMentions((prev) => new Map(prev).set(person!.username, userId));
              }} style={({ pressed }) => ({ flexDirection: 'row', gap: 8, paddingVertical: 10, paddingHorizontal: 16,
                backgroundColor: pressed ? colors.surfaceRaised : 'transparent' })}>
                <T weight="semibold">{displayName(person)}</T>
                <T color="textSecondary">@{person!.username}</T>
              </Pressable>
            ))}
          </View>
        ) : null}
        {notice ? (
          <View accessibilityLiveRegion="polite" style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 8, paddingHorizontal: 16, backgroundColor: colors.warningSoft }}>
            <Icon name="timer" size={16} color="warning" />
            <T variant="caption" weight="semibold" color="warning" style={{ flex: 1 }}>{notice}</T>
            <Pressable accessibilityRole="button" accessibilityLabel="Dismiss" hitSlop={12} onPress={() => setNotice(null)}>
              <Icon name="x" size={16} color="warning" />
            </Pressable>
          </View>
        ) : null}
        <View style={{ paddingBottom: keyboardUp ? 0 : insets.bottom, backgroundColor: colors.surface }}>
          <Composer value={text} onChangeText={typed} onSend={submit} onAttach={() => setAttachOpen(true)}
            editing={editing ? editing.body ?? '' : undefined} onCancelEdit={() => {
              setEditing(null);
              setText('');
            }} locked={c ? locked : undefined} />
        </View>
      </KeyboardAvoidingView>

      <MessageMenu visible={!!selected} mine={selected?.message.senderUserId === myId} anchor={selected?.at ?? null}
        onClose={() => setSelected(null)} actions={selected ? actions(selected.message) : []}
        reacted={selected?.message.reactions.filter((r) => r.userIds.includes(myId)).map((r) => r.emoji)}
        onReact={selected && !selected.message.local ? (emoji) => void react(selected.message, emoji) : undefined}
        preview={selected ? (
          <MessageBubble bare message={selected.message} mine={selected.message.senderUserId === myId} first myId={myId}
            delivery={delivery(selected.message)}
            sender={group && selected.message.senderUserId !== myId ? displayName(people.get(selected.message.senderUserId), selected.message.senderUserId) : undefined} />
        ) : null} />
      <ActionSheet visible={attachOpen} onClose={() => setAttachOpen(false)} title="Attach"
        note="Up to 100 MB a file and 200 files a day. Every file is checked for threats before anyone can open it."
        actions={[
          { label: 'Photos', icon: 'image', onPress: () => void attach(pickPhoto) },
          ...(Platform.OS === 'web' ? [] : [{ label: 'Camera', icon: 'camera' as const, onPress: () => void attach(takePhoto) }]),
          { label: 'File', icon: 'file', onPress: () => void attach(pickDocument) },
        ]} />
      <Dialog spec={dialog} onClose={() => setDialog(null)} />
    </Backdrop>
  );
}
