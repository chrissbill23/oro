import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { FlatList, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { oro } from '../../api/oro';
import { Backdrop } from '../../components/Backdrop';
import { Avatar, EmptyState, SectionHeader } from '../../components/Basics';
import { Button } from '../../components/Button';
import { SearchField } from '../../components/Fields';
import { LoadError, Spinner } from '../../components/States';
import { T } from '../../components/Text';
import { listTime } from '../../lib/format';
import { firstName, usePeople } from '../../lib/people';
import { useConversations, useTitle } from '../../lib/queries';
import { useSession } from '../../session/SessionProvider';
import { useTheme } from '../../theme/ThemeProvider';

/** the words found, in bold */
function Highlight({ text, q }: { text: string; q: string }) {
  const words = q.trim().split(/\s+/).filter(Boolean).map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  if (!words.length) {
    return <T variant="bodySmall" color="textSecondary" numberOfLines={2}>{text}</T>;
  }
  const parts = text.split(new RegExp(`(${words.join('|')})`, 'i'));
  return (
    <T variant="bodySmall" color="textSecondary" numberOfLines={2}>
      {parts.map((p, i) => (i % 2 ? <T key={i} variant="bodySmall" weight="bold" color="textPrimary">{p}</T> : p))}
    </T>
  );
}

/** B3 · Search: messages, newest first (B3b none, B3c searching); in one conversation from its info */
export default function Search() {
  const { conversationId, title: scope } = useLocalSearchParams<{ conversationId?: string; title?: string }>();
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const { me } = useSession();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setDebounced(q.trim()), 300);
    return () => clearTimeout(t);
  }, [q]);
  const results = useQuery({
    queryKey: ['search', debounced, conversationId],
    queryFn: () => oro.search(debounced, conversationId),
    enabled: debounced.length >= 2,
  });
  const conversations = useConversations();
  const byId = new Map((conversations.data ?? []).map((c) => [c.id, c]));
  const people = usePeople([...(results.data ?? []).map((m) => m.senderUserId), ...(conversations.data ?? []).map((c) => c.otherUserId)]);
  const title = useTitle();

  return (
    <Backdrop>
      <View style={{ paddingTop: insets.top + 8, paddingHorizontal: 16, paddingBottom: 8, flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <SearchField value={q} onChangeText={setQ} autoFocus placeholder={scope ? `Search in ${scope}` : 'Search messages'} />
        </View>
        <Pressable accessibilityRole="button" onPress={() => router.back()} hitSlop={8}><T color="accent">Cancel</T></Pressable>
      </View>
      {debounced.length < 2 ? null : results.isPending ? (
        <View style={{ alignItems: 'center', gap: 8 }}><Spinner /><T color="textSecondary">Searching…</T></View>
      ) : results.isError ? (
        <LoadError error={results.error} onRetry={() => results.refetch()} />
      ) : (
        <FlatList
          data={results.data}
          keyExtractor={(m) => m.id}
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={{ paddingBottom: insets.bottom + 24 }}
          ListHeaderComponent={results.data?.length ? <SectionHeader label="Messages · Newest first" /> : undefined}
          ListFooterComponent={results.data?.length ? (
            <T variant="caption" color="textTertiary" align="center" style={{ padding: 16 }}>Showing up to 30 of the newest messages</T>
          ) : undefined}
          ListEmptyComponent={
            <EmptyState icon="search" title="No messages found" body={`No message contains “${debounced}”. Try fewer or different words.`}
              action={<Button label="Start a conversation" kind="secondary" onPress={() => router.replace('/new-message')} style={{ alignSelf: 'stretch', marginTop: 8 }} />} />
          }
          renderItem={({ item: m }) => {
            const c = byId.get(m.conversationId);
            const name = c ? title(c, people.get) : 'Conversation';
            const group = c?.type === 'GROUP';
            const prefix = m.senderUserId === me?.userId ? 'You: ' : group ? `${firstName(people.get(m.senderUserId), m.senderUserId)}: ` : '';
            return (
              <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/chat/[id]', params: { id: m.conversationId } })}
                style={({ pressed }) => ({ flexDirection: 'row', gap: 12, paddingVertical: 10, paddingHorizontal: 16,
                  backgroundColor: pressed ? colors.surfaceRaised : 'transparent' })}>
                <Avatar name={name} id={group ? m.conversationId : c?.otherUserId ?? m.conversationId} group={group} />
                <View style={{ flex: 1, gap: 2 }}>
                  <View style={{ flexDirection: 'row', gap: 8 }}>
                    <T weight="bold" numberOfLines={1} style={{ flex: 1 }}>{name}</T>
                    <T variant="caption" color="textTertiary">{listTime(m.createdAt)}</T>
                  </View>
                  <Highlight text={`${prefix}${m.body ?? ''}`} q={debounced} />
                </View>
              </Pressable>
            );
          }}
        />
      )}
    </Backdrop>
  );
}

