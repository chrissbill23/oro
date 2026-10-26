import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, RefreshControl, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Backdrop } from '../../../components/Backdrop';
import { EmptyState } from '../../../components/Basics';
import { Button, IconButton } from '../../../components/Button';
import { Icon } from '../../../components/Icon';
import { LargeNav } from '../../../components/Nav';
import { ConversationRow } from '../../../components/Rows';
import { ConnectionStatus, ListSkeleton, LoadError } from '../../../components/States';
import { T } from '../../../components/Text';
import { useLive } from '../../../live/LiveProvider';
import { listTime, preview } from '../../../lib/format';
import { displayName, firstName } from '../../../lib/people';
import { isMuted, useConversations, usePeopleOf, useRequests, useTitle } from '../../../lib/queries';
import { useSession } from '../../../session/SessionProvider';
import { useTheme } from '../../../theme/ThemeProvider';

/** B1 · Chats: search, the requests waiting, the conversations newest first; B2 empty, J1 loading, J3/J4 errors */
export default function Chats() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { me } = useSession();
  const live = useLive();
  const conversations = useConversations();
  const requests = useRequests();
  const people = usePeopleOf([...(conversations.data ?? []), ...(requests.data ?? [])]);
  const title = useTitle();
  const [refreshing, setRefreshing] = useState(false);

  const waiting = requests.data ?? [];
  const list = conversations.data ?? [];

  const header = (
    <View style={{ gap: 8, paddingBottom: 4 }}>
      <Pressable accessibilityRole="search" accessibilityLabel="Search messages" onPress={() => router.push('/search')}
        style={{ marginHorizontal: 16, height: 40, borderRadius: 12, paddingHorizontal: 12, gap: 8, flexDirection: 'row',
          alignItems: 'center', backgroundColor: colors.surfaceRaised }}>
        <Icon name="search" size={18} color="textSecondary" />
        <T color="textSecondary">Search messages</T>
      </Pressable>
      {waiting.length > 0 ? (
        <Pressable accessibilityRole="button" onPress={() => router.navigate('/requests')}
          style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingHorizontal: 16,
            backgroundColor: pressed ? colors.surfaceRaised : 'transparent' })}>
          <View style={{ width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.accentSoft }}>
            <Icon name="inbox" size={20} color="accent" />
          </View>
          <View style={{ flex: 1 }}>
            <T weight="bold">Message requests · {waiting.length}</T>
            <T variant="bodySmall" color="textSecondary" numberOfLines={1}>
              {waiting.map((r) => (r.type === 'GROUP' ? r.name : displayName(people.get(r.otherUserId ?? r.invitedByUserId), r.otherUserId))).join(', ')}
            </T>
          </View>
          <Icon name="chevron-right" size={18} color="textTertiary" />
        </Pressable>
      ) : null}
    </View>
  );

  let body;
  if (conversations.isPending) {
    body = <>{header}<ListSkeleton /></>;
  } else if (conversations.isError && !conversations.data) {
    body = <LoadError error={conversations.error} onRetry={() => conversations.refetch()} />;
  } else {
    body = (
      <FlashList
        data={list}
        keyExtractor={(c) => c.id}
        ListHeaderComponent={header}
        contentContainerStyle={{ paddingBottom: insets.bottom + 96 }}
        refreshControl={<RefreshControl refreshing={refreshing} tintColor={colors.accent} onRefresh={async () => {
          setRefreshing(true);
          await Promise.all([conversations.refetch(), requests.refetch()]);
          setRefreshing(false);
        }} />}
        ListEmptyComponent={
          <EmptyState icon="message-circle" title="No conversations yet"
            body="Say hello to someone you know. Only the people you talk to see when you're online."
            action={<Button label="Start a conversation" onPress={() => router.push('/new-message')} style={{ marginTop: 8, alignSelf: 'stretch' }} />} />
        }
        renderItem={({ item: c }) => {
          const typing = live.typing(c.id).filter((u) => u !== me?.userId);
          const group = c.type === 'GROUP';
          return (
            <ConversationRow
              id={c.id}
              name={title(c, people.get)}
              group={group}
              online={!group && !!c.otherUserId && live.online.has(c.otherUserId)}
              time={listTime(c.lastMessageAt ?? c.createdAt)}
              preview={preview(c.lastMessage, people.get, me?.userId, group)}
              typing={typing.length ? (group ? `${firstName(people.get(typing[0]), typing[0])} is typing…` : 'typing…') : undefined}
              unread={c.unreadCount}
              muted={isMuted(c.mutedUntil)}
              onPress={() => router.push({ pathname: '/chat/[id]', params: { id: c.id } })}
            />
          );
        }}
      />
    );
  }

  return (
    <Backdrop>
      <LargeNav title="Chats" subtitle={<ConnectionStatus status={live.status} />}
        right={<IconButton name="square-pen" label="New message" filled onPress={() => router.push('/new-message')} />} />
      {body}
    </Backdrop>
  );
}
