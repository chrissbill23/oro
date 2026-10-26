import { LastMessage, Message, Person } from '../api/types';
import { displayName } from './people';

const sameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

/** 14:05 */
export function clock(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** the time of a conversation in the list: today 14:05, Yesterday, Mon, 12 Oct */
export function listTime(iso: string | undefined, now = new Date()): string {
  if (!iso) {
    return '';
  }
  const d = new Date(iso);
  if (sameDay(d, now)) {
    return clock(iso);
  }
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (sameDay(d, yesterday)) {
    return 'Yesterday';
  }
  if (now.getTime() - d.getTime() < 6 * 86400000) {
    return d.toLocaleDateString(undefined, { weekday: 'short' });
  }
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

/** the label of a date separator: Today, Yesterday, Monday 12 October */
export function dayLabel(iso: string, now = new Date()): string {
  const d = new Date(iso);
  if (sameDay(d, now)) {
    return 'Today';
  }
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (sameDay(d, yesterday)) {
    return 'Yesterday';
  }
  return d.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' });
}

export function isSameDay(a: string, b: string): boolean {
  return sameDay(new Date(a), new Date(b));
}

/** 1.2 MB */
export function fileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

type Named = (userId: string | undefined) => Person | undefined;

/** a system message in words: "Amani joined", "Juma removed Amani", "Juma renamed the group to Trip" */
export function systemText(m: Pick<Message, 'metadata' | 'body'>, person: Named, me?: string): string {
  const meta = m.metadata;
  if (!meta || !('event' in meta)) {
    return m.body ?? '';
  }
  const who = (id?: string) => (id && id === me ? 'You' : displayName(person(id), id));
  switch (meta.event) {
    case 'member.joined':
      return meta.byUserId && meta.byUserId !== meta.userId
        ? `${who(meta.byUserId)} added ${who(meta.userId)}`
        : `${who(meta.userId)} joined`;
    case 'member.left':
      return `${who(meta.userId)} left`;
    case 'member.removed':
      return meta.byUserId ? `${who(meta.byUserId)} removed ${who(meta.userId)}` : `${who(meta.userId)} was removed`;
    case 'conversation.renamed':
      return meta.name ? `The group was renamed to ${meta.name}` : 'The group was renamed';
    default:
      return m.body ?? '';
  }
}

/** the preview line of a conversation in the list */
export function preview(last: LastMessage | undefined, person: Named, me: string | undefined, group: boolean): string {
  if (!last) {
    return 'No messages yet';
  }
  if (last.deleted) {
    return 'Message deleted';
  }
  if (last.type === 'SYSTEM') {
    return systemText(last as Pick<Message, 'metadata' | 'body'>, person, me);
  }
  const text = last.type === 'IMAGE' ? `📷 ${last.body || 'Photo'}` : last.type === 'FILE' ? `📎 ${last.body || 'File'}` : last.body ?? '';
  if (last.senderUserId === me) {
    return `You: ${text}`;
  }
  return group ? `${displayName(person(last.senderUserId), last.senderUserId).split(' ')[0]}: ${text}` : text;
}

/** a client message id: Oro's idempotency key of a send ([A-Za-z0-9._:-]{8,64}) */
export function clientId(): string {
  const r = () => Math.random().toString(36).slice(2, 10);
  return `m-${Date.now().toString(36)}-${r()}${r()}`;
}
