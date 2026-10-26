import { createContext, PropsWithChildren, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { oro } from '../api/oro';
import { Person } from '../api/types';

/** a person as the screens name it: given name and surname, else the username; an erased person: "Deleted account" */
export function displayName(person: Person | undefined, userId?: string): string {
  if (person) {
    const full = [person.givenName, person.surname].filter(Boolean).join(' ').trim();
    return full || person.username;
  }
  if (userId?.startsWith('erased-')) {
    return 'Deleted account';
  }
  return 'Someone';
}

export function firstName(person: Person | undefined, userId?: string): string {
  return person?.givenName || displayName(person, userId).split(' ')[0];
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  return ((parts[0][0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

interface People {
  get: (userId: string | undefined) => Person | undefined;
  want: (userIds: (string | undefined)[]) => void;
}

const PeopleContext = createContext<People>({ get: () => undefined, want: () => {} });

/**
 * The names of the people of one's conversations (GET /people), asked in batches of what the screens show, kept for
 * the session. Someone Oro does not name (a stranger, an erased person) stays unknown.
 */
export function PeopleProvider({ children }: PropsWithChildren) {
  const [known, setKnown] = useState<Map<string, Person>>(new Map());
  const asked = useRef(new Set<string>());
  const queue = useRef(new Set<string>());
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const flush = useCallback(async () => {
    timer.current = null;
    // 200 at a time (the most Oro answers at once), until the queue is empty
    while (queue.current.size > 0) {
      const ids = [...queue.current].slice(0, 200);
      ids.forEach((id) => queue.current.delete(id));
      try {
        const people = await oro.people(ids);
        setKnown((prev) => {
          const next = new Map(prev);
          people.forEach((p) => next.set(p.userId, p));
          return next;
        });
      } catch {
        // asked again by the next screen that shows them
        ids.forEach((id) => asked.current.delete(id));
        return;
      }
    }
  }, []);

  const want = useCallback(
    (userIds: (string | undefined)[]) => {
      for (const id of userIds) {
        if (id && !asked.current.has(id) && !id.startsWith('erased-')) {
          asked.current.add(id);
          queue.current.add(id);
        }
      }
      if (queue.current.size > 0 && !timer.current) {
        timer.current = setTimeout(flush, 30);
      }
    },
    [flush],
  );

  const get = useCallback((userId: string | undefined) => (userId ? known.get(userId) : undefined), [known]);
  return <PeopleContext.Provider value={{ get, want }}>{children}</PeopleContext.Provider>;
}

/** the people of these user ids, asked when not known yet */
export function usePeople(userIds: (string | undefined)[]): People {
  const people = useContext(PeopleContext);
  const key = userIds.filter(Boolean).join(',');
  useEffect(() => {
    people.want(userIds);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, people.want]);
  return people;
}
