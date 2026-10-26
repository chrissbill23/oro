import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { useState } from 'react';
import { RefreshControl, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Backdrop } from '../../../components/Backdrop';
import { EmptyState, SectionHeader } from '../../../components/Basics';
import { Button } from '../../../components/Button';
import { LargeNav } from '../../../components/Nav';
import { RequestRow } from '../../../components/Rows';
import { ListSkeleton, LoadError } from '../../../components/States';
import { T } from '../../../components/Text';
import { listTime, preview } from '../../../lib/format';
import { displayName, firstName } from '../../../lib/people';
import { usePeopleOf, useRequests } from '../../../lib/queries';
import { useSession } from '../../../session/SessionProvider';
import { useTheme } from '../../../theme/ThemeProvider';

/** C1 · Requests: who you don't know waits here (a message request, a group invitation); C4 empty */
export default function Requests() {
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const { me } = useSession();
  const requests = useRequests();
  const people = usePeopleOf(requests.data);
  const [refreshing, setRefreshing] = useState(false);
  const list = requests.data ?? [];

  return (
    <Backdrop>
      <LargeNav title="Requests" />
      {requests.isPending ? <ListSkeleton rows={3} /> : requests.isError && !requests.data ? (
        <LoadError error={requests.error} onRetry={() => requests.refetch()} />
      ) : (
        <FlashList
          data={list}
          keyExtractor={(c) => c.id}
          contentContainerStyle={{ paddingBottom: insets.bottom + 96 }}
          refreshControl={<RefreshControl refreshing={refreshing} tintColor={colors.accent} onRefresh={async () => {
            setRefreshing(true);
            await requests.refetch();
            setRefreshing(false);
          }} />}
          ListHeaderComponent={list.length ? (
            <View>
              <T variant="bodySmall" color="textSecondary" style={{ paddingHorizontal: 16, lineHeight: 20 }}>
                People you don&apos;t know can&apos;t write to you directly. Their first message waits here until you decide.
              </T>
              <SectionHeader label={`Waiting · ${list.length}`} />
            </View>
          ) : null}
          ListEmptyComponent={
            <EmptyState icon="inbox" title="No requests" body="When someone you don't know writes to you, it arrives here first."
              action={<Button label="Start a conversation" kind="secondary" onPress={() => router.push('/new-message')} style={{ marginTop: 8, alignSelf: 'stretch' }} />} />
          }
          renderItem={({ item: r }) => {
            const group = r.type === 'GROUP';
            const inviter = r.invitedByUserId ?? r.otherUserId;
            return (
              <RequestRow
                id={r.id}
                name={group ? r.name ?? 'Group' : displayName(people.get(r.otherUserId), r.otherUserId)}
                kind={group ? `${firstName(people.get(inviter), inviter)} invited you · ${r.memberCount} members` : 'Message request'}
                preview={preview(r.lastMessage, people.get, me?.userId, group)}
                time={listTime(r.lastMessageAt ?? r.createdAt)}
                onPress={() => router.push({ pathname: '/request/[id]', params: { id: r.id } })}
              />
            );
          }}
        />
      )}
    </Backdrop>
  );
}
