import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { oro } from '../api/oro';
import { Message } from '../api/types';
import { ChatMessage } from '../components/Chat';
import { useLive } from '../live/LiveProvider';
import { clientId } from './format';
import { Picked } from './files';

export type SendProblem = 'rateLimited' | 'oneMessage' | 'filesToday' | 'tooLarge' | 'quota' | 'other';

export function problemOf(e: unknown): SendProblem {
  if (e instanceof ApiError) {
    if (e.status === 429 && /files/i.test(e.message)) return 'filesToday';
    if (e.status === 429) return 'rateLimited';
    if (e.status === 409 && /one message/i.test(e.message)) return 'oneMessage';
    if (e.status === 413) return 'quota';
  }
  return 'other';
}

/** newest first, by seq; what is still sending stays on top */
export function merge(current: ChatMessage[], incoming: Message[]): ChatMessage[] {
  const byId = new Map(current.filter((m) => !m.local).map((m) => [m.id, m]));
  const sentClientIds = new Set<string>();
  for (const m of incoming) {
    byId.set(m.id, m);
    if (m.clientMessageId) {
      sentClientIds.add(m.clientMessageId);
    }
  }
  const local = current.filter((m) => m.local && !(m.clientMessageId && sentClientIds.has(m.clientMessageId)));
  return [...local, ...[...byId.values()].sort((a, b) => b.seq - a.seq)];
}

/**
 * The messages of an open conversation: the newest page, the older ones on demand, what the stream says (a new
 * message read with every one missed before it: no gap; an edit, a delete, a reaction read again), and the catch-up
 * on every hello. Sends are shown at once and kept on failure, to retry with the same client id (Oro's idempotency).
 */
export function useMessages(conversationId: string) {
  const live = useLive();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [olderFrom, setOlderFrom] = useState<number | null | undefined>(undefined);
  const newest = useRef(0);
  const catching = useRef<Promise<void> | null>(null);
  const loadingOlder = useRef(false);

  const accept = useCallback((incoming: Message[]) => {
    for (const m of incoming) {
      newest.current = Math.max(newest.current, m.seq);
    }
    setMessages((current) => merge(current, incoming));
  }, []);

  /** every message after the newest known, page by page: what the stream did not bring */
  const catchUp = useCallback(() => {
    if (!catching.current) {
      catching.current = (async () => {
        try {
          for (let i = 0; i < 20; i++) {
            const page = await oro.after(conversationId, newest.current);
            accept(page.messages);
            if (page.messages.length < 100) {
              break;
            }
          }
        } catch {
          // the next hello catches up
        }
      })().finally(() => {
        catching.current = null;
      });
    }
    return catching.current;
  }, [accept, conversationId]);

  /** the newest page; its state set when Oro answers */
  const fetchNewest = useCallback(
    () =>
      oro.newest(conversationId).then(
        (page) => {
          accept(page.messages);
          setOlderFrom(page.nextBeforeSeq ?? null);
          setError(null);
          setLoading(false);
        },
        (e) => {
          setError(e);
          setLoading(false);
        },
      ),
    [accept, conversationId],
  );

  // a screen per conversation: its first page once
  useEffect(() => {
    void fetchNewest();
  }, [fetchNewest]);

  /** Try again (J4) */
  const load = useCallback(() => {
    setLoading(true);
    void fetchNewest();
  }, [fetchNewest]);

  const loadOlder = useCallback(async () => {
    if (!olderFrom || loadingOlder.current) {
      return;
    }
    loadingOlder.current = true;
    try {
      const page = await oro.before(conversationId, olderFrom);
      accept(page.messages);
      setOlderFrom(page.nextBeforeSeq ?? null);
    } catch {
      // asked again on the next scroll
    } finally {
      loadingOlder.current = false;
    }
  }, [accept, conversationId, olderFrom]);

  useEffect(
    () =>
      live.subscribe((event) => {
        if (event.type === 'hello') {
          void catchUp();
        } else if (event.type === 'message' && event.conversationId === conversationId) {
          if (event.change === 'created') {
            void catchUp();
          } else {
            oro.message(event.messageId).then((m) => accept([m]), () => undefined);
          }
        }
      }),
    [accept, catchUp, conversationId, live],
  );

  const replaceLocal = useCallback((id: string, change: Partial<ChatMessage> | null, sent?: Message) => {
    setMessages((current) => {
      const without = change === null ? current.filter((m) => m.id !== id) : current.map((m) => (m.id === id ? { ...m, ...change } : m));
      return sent ? merge(without.filter((m) => m.id !== id), [sent]) : without;
    });
    if (sent) {
      newest.current = Math.max(newest.current, sent.seq);
    }
  }, []);

  /** a message sent: shown at once (sending), then Oro's; kept as failed when it did not go */
  const send = useCallback(
    async (body: string, mentions: string[], me: string, retry?: ChatMessage): Promise<SendProblem | null> => {
      const cid = retry?.clientMessageId ?? clientId();
      const id = retry?.id ?? `local-${cid}`;
      const local: ChatMessage = retry
        ? { ...retry, local: 'sending' }
        : {
            id, conversationId, seq: Number.MAX_SAFE_INTEGER, senderUserId: me, clientMessageId: cid, type: 'TEXT', body,
            createdAt: new Date().toISOString(), deleted: false, reactions: [], attachments: [], local: 'sending',
          };
      setMessages((current) => [local, ...current.filter((m) => m.id !== id)]);
      try {
        const sent = await oro.send(conversationId, {
          clientMessageId: cid, type: 'TEXT', body: local.body, metadata: mentions.length ? { mentions } : undefined,
        });
        replaceLocal(id, null, sent);
        return null;
      } catch (e) {
        const problem = problemOf(e);
        replaceLocal(id, problem === 'oneMessage' ? null : { local: 'failed' });
        return problem;
      }
    },
    [conversationId, replaceLocal],
  );

  /** a file sent: uploaded (scanned by Oro), then its message */
  const sendFile = useCallback(
    async (file: Picked, me: string, retry?: ChatMessage): Promise<SendProblem | null> => {
      const cid = retry?.clientMessageId ?? clientId();
      const id = retry?.id ?? `local-${cid}`;
      const type = file.type.startsWith('image/') ? 'IMAGE' : 'FILE';
      const local: ChatMessage = {
        id, conversationId, seq: Number.MAX_SAFE_INTEGER, senderUserId: me, clientMessageId: cid, type,
        createdAt: new Date().toISOString(), deleted: false, reactions: [], attachments: [], local: 'sending',
        pendingFile: file,
      };
      setMessages((current) => [local, ...current.filter((m) => m.id !== id)]);
      try {
        const attachment = await oro.upload(conversationId, file.web ?? { uri: file.uri, name: file.name, type: file.type }, file.name);
        const sent = await oro.send(conversationId, { clientMessageId: cid, type, attachmentIds: [attachment.id] });
        replaceLocal(id, null, sent);
        return null;
      } catch (e) {
        const problem = problemOf(e);
        replaceLocal(id, problem === 'filesToday' || problem === 'quota' ? null : { local: 'failed' });
        return problem;
      }
    },
    [conversationId, replaceLocal],
  );

  return { messages, loading, error, reload: load, loadOlder, hasOlder: !!olderFrom, accept, send, sendFile, discard: (id: string) => replaceLocal(id, null) };
}
