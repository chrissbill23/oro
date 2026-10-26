import { useMemo } from 'react';
import { useSession } from '../session/SessionProvider';
import { usePeople } from './people';
import { useBlocks, useConversations } from './queries';

/**
 * Your contacts: the people of your direct conversations (Oro has no search of people yet: finding someone new
 * needs the backend). Blocked and erased people are left out.
 */
export function useContacts() {
  const { me } = useSession();
  const conversations = useConversations();
  const blocks = useBlocks();
  const ids = useMemo(() => {
    const blocked = new Set(blocks.data ?? []);
    const seen = new Set<string>();
    for (const c of conversations.data ?? []) {
      const id = c.otherUserId;
      if (c.type === 'DIRECT' && id && id !== me?.userId && !blocked.has(id) && !id.startsWith('erased-')) {
        seen.add(id);
      }
    }
    return [...seen];
  }, [blocks.data, conversations.data, me?.userId]);
  const people = usePeople(ids);
  const sorted = useMemo(
    () => ids.slice().sort((a, b) => (people.get(a)?.givenName ?? '~').localeCompare(people.get(b)?.givenName ?? '~')),
    [ids, people],
  );
  return { ids: sorted, people, loading: conversations.isPending };
}
