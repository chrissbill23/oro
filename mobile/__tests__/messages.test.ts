import { Message } from '../src/api/types';
import { ChatMessage } from '../src/components/Chat';
import { merge } from '../src/lib/messages';

const msg = (seq: number, extra: Partial<Message> = {}): Message => ({
  id: `m${seq}`, conversationId: 'c', seq, senderUserId: 'u', type: 'TEXT', body: String(seq), createdAt: '',
  deleted: false, reactions: [], attachments: [], ...extra,
});

describe('merge', () => {
  it('keeps the messages newest first, once each, the newer version winning', () => {
    const merged = merge([msg(2), msg(1)], [msg(3), msg(2, { body: 'edited' })]);
    expect(merged.map((m) => m.seq)).toEqual([3, 2, 1]);
    expect(merged[1].body).toBe('edited');
  });

  it('drops a message still sending once Oro has it (same client id), keeps the others on top', () => {
    const sending: ChatMessage = { ...msg(Number.MAX_SAFE_INTEGER), id: 'local-a', clientMessageId: 'a', local: 'sending' };
    const failed: ChatMessage = { ...msg(Number.MAX_SAFE_INTEGER), id: 'local-b', clientMessageId: 'b', local: 'failed' };
    const merged = merge([sending, failed, msg(1)], [msg(2, { clientMessageId: 'a' })]);
    expect(merged.map((m) => m.id)).toEqual(['local-b', 'm2', 'm1']);
  });
});
