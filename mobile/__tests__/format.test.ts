import { clientId, dayLabel, fileSize, listTime, preview, systemText } from '../src/lib/format';
import { displayName, initials } from '../src/lib/people';

const people: Record<string, { userId: string; givenName?: string; surname?: string; username: string }> = {
  a: { userId: 'a', givenName: 'Amani', surname: 'Okello', username: 'amani' },
  j: { userId: 'j', givenName: 'Juma', surname: 'Bakari', username: 'juma' },
};
const get = (id?: string) => (id ? people[id] : undefined);

describe('format', () => {
  const now = new Date(2026, 9, 26, 15, 0);

  it('writes the time of a conversation like the design', () => {
    expect(listTime(new Date(2026, 9, 26, 9, 5).toISOString(), now)).toBe('09:05');
    expect(listTime(new Date(2026, 9, 25, 22, 0).toISOString(), now)).toBe('Yesterday');
    expect(dayLabel(new Date(2026, 9, 26, 1, 0).toISOString(), now)).toBe('Today');
  });

  it('writes sizes', () => {
    expect([fileSize(900), fileSize(2048), fileSize(1572864)]).toEqual(['900 B', '2 KB', '1.5 MB']);
  });

  it('says the system events in words', () => {
    expect(systemText({ metadata: { event: 'member.joined', userId: 'a', byUserId: 'j' } }, get, 'j')).toBe('You added Amani Okello');
    expect(systemText({ metadata: { event: 'member.left', userId: 'a' } }, get)).toBe('Amani Okello left');
    expect(systemText({ metadata: { event: 'member.left', userId: 'erased-1' } }, get)).toBe('Deleted account left');
  });

  it('previews the last message', () => {
    const last = { id: '1', seq: 1, type: 'TEXT' as const, body: 'Hi', deleted: false, createdAt: '' };
    expect(preview({ ...last, senderUserId: 'j' }, get, 'j', true)).toBe('You: Hi');
    expect(preview({ ...last, senderUserId: 'a' }, get, 'j', true)).toBe('Amani: Hi');
    expect(preview({ ...last, senderUserId: 'a', deleted: true }, get, 'j', false)).toBe('Message deleted');
  });

  it('names people', () => {
    expect(displayName(people.a)).toBe('Amani Okello');
    expect(displayName({ userId: 'x', username: 'x.y' })).toBe('x.y');
    expect(initials('Amani Okello')).toBe('AO');
  });

  it('makes client ids Oro accepts', () => {
    expect(clientId()).toMatch(/^[A-Za-z0-9._:-]{8,64}$/);
  });
});
