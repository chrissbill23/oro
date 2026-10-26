import { QueryClient, useQueryClient } from '@tanstack/react-query';
import { createContext, PropsWithChildren, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { CLIENT_HEADERS, getAccessToken, refreshSession } from '../api/client';
import { ORO_URL } from '../api/config';
import { oro } from '../api/oro';
import { ConversationSummary, LiveEvent } from '../api/types';
import { useSession } from '../session/SessionProvider';
import { nextDelay, SseParser } from './sse';

/** connecting (no stream yet), live, offline (no network: retrying), updating (catching up after a cut) */
export type LiveStatus = 'connecting' | 'live' | 'offline' | 'updating';

type Listener = (event: LiveEvent) => void;

interface Live {
  status: LiveStatus;
  /** the user ids online (contacts only) */
  online: Set<string>;
  /** who types in a conversation, now */
  typing: (conversationId: string) => string[];
  subscribe: (listener: Listener) => () => void;
}

const LiveContext = createContext<Live | null>(null);

const TYPING_FOR = 5000;

/** the list of conversations follows the stream: a new message moves its conversation up with its preview */
async function applyMessage(client: QueryClient, event: Extract<LiveEvent, { type: 'message' }>, me?: string) {
  const list = client.getQueryData<ConversationSummary[]>(['conversations']);
  const known = list?.find((c) => c.id === event.conversationId);
  if (!list || !known) {
    await client.invalidateQueries({ queryKey: ['conversations'] });
    await client.invalidateQueries({ queryKey: ['requests'] });
    return;
  }
  if (event.change !== 'created' && known.lastMessage?.id !== event.messageId) {
    return;
  }
  if (event.change === 'created' && event.seq <= known.lastMessageSeq) {
    return;
  }
  try {
    const m = await oro.message(event.messageId);
    client.setQueryData<ConversationSummary[]>(['conversations'], (current) => {
      if (!current) {
        return current;
      }
      const next = current.map((c) => {
        if (c.id !== event.conversationId) {
          return c;
        }
        const preview = {
          id: m.id, seq: m.seq, senderUserId: m.senderUserId, type: m.type, body: m.body?.slice(0, 200),
          metadata: m.type === 'SYSTEM' ? m.metadata : undefined, deleted: m.deleted, createdAt: m.createdAt,
        };
        if (event.change !== 'created') {
          return { ...c, lastMessage: preview };
        }
        const mine = m.senderUserId === me;
        return {
          ...c, lastMessage: preview, lastMessageSeq: m.seq, lastMessageAt: m.createdAt,
          unreadCount: mine ? 0 : c.unreadCount + Math.max(1, m.seq - c.lastMessageSeq),
        };
      });
      return next.sort((a, b) => (b.lastMessageAt ?? b.createdAt).localeCompare(a.lastMessageAt ?? a.createdAt));
    });
  } catch {
    await client.invalidateQueries({ queryKey: ['conversations'] });
  }
}

/**
 * The stream of the person (GET /stream, Server-Sent Events): opened while signed in, again after every cut with
 * Umoja's delays or Oro's Retry-After. Every hello (the first too) catches up: the lists read again, the open
 * conversation reads what it missed (it listens). Nothing is lost: what the stream missed, the catch-up reads.
 */
export function LiveProvider({ children }: PropsWithChildren) {
  const { state, me } = useSession();
  const client = useQueryClient();
  const [status, setStatus] = useState<LiveStatus>('connecting');
  const [online, setOnline] = useState<Set<string>>(new Set());
  const [typingMap, setTypingMap] = useState<Map<string, Map<string, number>>>(new Map());
  const listeners = useRef(new Set<Listener>());
  const meId = me?.userId;

  const emit = useCallback((event: LiveEvent) => listeners.current.forEach((l) => l(event)), []);

  const handle = useCallback(
    async (event: LiveEvent) => {
      switch (event.type) {
        case 'hello':
          setOnline(new Set(event.online));
          setStatus('updating');
          await Promise.all([
            client.invalidateQueries({ queryKey: ['conversations'] }),
            client.invalidateQueries({ queryKey: ['requests'] }),
          ]);
          setStatus('live');
          break;
        case 'presence':
          setOnline((prev) => {
            const next = new Set(prev);
            if (event.online) {
              next.add(event.userId);
            } else {
              next.delete(event.userId);
            }
            return next;
          });
          break;
        case 'typing':
          setTypingMap((prev) => {
            const next = new Map(prev);
            const inConversation = new Map(next.get(event.conversationId) ?? []);
            inConversation.set(event.userId, Date.now() + TYPING_FOR);
            next.set(event.conversationId, inConversation);
            return next;
          });
          break;
        case 'message':
          if (event.change === 'created') {
            setTypingMap((prev) => {
              const next = new Map(prev);
              next.delete(event.conversationId);
              return next;
            });
          }
          await applyMessage(client, event, meId);
          break;
        case 'conversation':
          await Promise.all([
            client.invalidateQueries({ queryKey: ['conversations'] }),
            client.invalidateQueries({ queryKey: ['requests'] }),
            client.invalidateQueries({ queryKey: ['conversation', event.conversationId] }),
          ]);
          break;
        case 'read':
          if (event.userId === meId) {
            client.setQueryData<ConversationSummary[]>(['conversations'], (list) =>
              list?.map((c) =>
                c.id === event.conversationId ? { ...c, unreadCount: Math.max(0, c.lastMessageSeq - event.seq) } : c,
              ),
            );
          }
          break;
        case 'reconnect':
          break;
      }
      emit(event);
    },
    [client, emit, meId],
  );

  useEffect(() => {
    if (state !== 'signedIn') {
      return;
    }
    let stopped = false;
    let controller: AbortController | null = null;
    let wake: (() => void) | null = null;
    const sleep = (ms: number) =>
      new Promise<void>((resolve) => {
        const t = setTimeout(resolve, ms);
        wake = () => {
          clearTimeout(t);
          resolve();
        };
      });

    (async () => {
      let attempt = 0;
      while (!stopped) {
        controller = new AbortController();
        let retryAfter: number | undefined;
        try {
          let res = await fetch(`${ORO_URL}/stream`, {
            headers: { ...CLIENT_HEADERS, Accept: 'text/event-stream', Authorization: `Bearer ${getAccessToken() ?? ''}` },
            signal: controller.signal,
          });
          if (res.status === 401 && (await refreshSession())) {
            res = await fetch(`${ORO_URL}/stream`, {
              headers: { ...CLIENT_HEADERS, Accept: 'text/event-stream', Authorization: `Bearer ${getAccessToken() ?? ''}` },
              signal: controller.signal,
            });
          }
          if (!res.ok || !res.body) {
            const header = res.headers.get('Retry-After');
            retryAfter = header ? Number(header) : undefined;
            setStatus((s) => (s === 'live' ? 'connecting' : s));
          } else {
            const reader = res.body.getReader();
            const parser = new SseParser((data) => {
              try {
                const event = JSON.parse(data) as LiveEvent;
                if (event.type === 'hello') {
                  attempt = 0;
                }
                void handle(event);
                if (event.type === 'reconnect') {
                  controller?.abort();
                }
              } catch {
                // not an event of Oro: skipped
              }
            });
            while (!stopped) {
              const { done, value } = await reader.read();
              if (done) {
                break;
              }
              parser.push(value);
            }
            setStatus('connecting');
          }
        } catch (e) {
          if (!stopped && (e as Error)?.name !== 'AbortError') {
            setStatus('offline');
          }
        }
        if (stopped) {
          break;
        }
        await sleep(nextDelay(attempt++, retryAfter));
      }
    })();

    return () => {
      stopped = true;
      controller?.abort();
      const w = wake as (() => void) | null;
      w?.();
    };
  }, [state, handle]);

  // typing ends by itself after a few seconds
  useEffect(() => {
    const t = setInterval(() => {
      setTypingMap((prev) => {
        const now = Date.now();
        let changed = false;
        const next = new Map<string, Map<string, number>>();
        prev.forEach((users, conversationId) => {
          const left = new Map([...users].filter(([, until]) => until > now));
          changed ||= left.size !== users.size;
          if (left.size) {
            next.set(conversationId, left);
          }
        });
        return changed ? next : prev;
      });
    }, 1000);
    return () => clearInterval(t);
  }, []);

  const typing = useCallback(
    (conversationId: string) => [...(typingMap.get(conversationId)?.keys() ?? [])],
    [typingMap],
  );
  const subscribe = useCallback((listener: Listener) => {
    listeners.current.add(listener);
    return () => {
      listeners.current.delete(listener);
    };
  }, []);

  return (
    <LiveContext.Provider value={{ status, online, typing, subscribe }}>{children}</LiveContext.Provider>
  );
}

export function useLive(): Live {
  const live = useContext(LiveContext);
  if (!live) {
    throw new Error('useLive outside LiveProvider');
  }
  return live;
}
