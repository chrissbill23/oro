import { useQuery } from '@tanstack/react-query';
import { oro } from '../api/oro';
import { ConversationSummary } from '../api/types';
import { useSession } from '../session/SessionProvider';
import { displayName, usePeople } from './people';

export function useConversations() {
  return useQuery({ queryKey: ['conversations'], queryFn: oro.conversations });
}

export function useRequests() {
  return useQuery({ queryKey: ['requests'], queryFn: oro.requests });
}

export function useBlocks() {
  return useQuery({ queryKey: ['blocks'], queryFn: oro.blocks });
}

export function isMuted(mutedUntil?: string): boolean {
  return !!mutedUntil && new Date(mutedUntil).getTime() > Date.now();
}

/** the people a list shows: the other of each direct, the sender of each last message, who invited */
export function usePeopleOf(list: ConversationSummary[] | undefined) {
  const ids = (list ?? []).flatMap((c) => [c.otherUserId, c.lastMessage?.senderUserId, c.invitedByUserId]);
  return usePeople(ids);
}

/** the name of a conversation: a group's own, the other person's for a direct */
export function useTitle() {
  const { me } = useSession();
  return (c: Pick<ConversationSummary, 'type' | 'name' | 'otherUserId'>, person: ReturnType<typeof usePeople>['get']) =>
    c.type === 'GROUP' ? c.name ?? 'Group' : c.otherUserId === me?.userId ? 'You' : displayName(person(c.otherUserId), c.otherUserId);
}
