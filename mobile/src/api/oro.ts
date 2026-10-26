import { request } from './client';
import {
  Attachment, Conversation, ConversationSummary, Me, Message, MessagePage, Person, ReportCategory, SignedIn,
} from './types';

/** every call of Oro the app makes (backend README, API) */
export const oro = {
  // ---------- accounts (public) ----------
  login: (login: string, password: string) =>
    request<SignedIn>('/auth/login', { method: 'POST', auth: false, body: { login, password, remember: true } }),
  signUp: (body: { email: string; username: string; password: string; givenName: string; surname: string }) =>
    request<{ message: string }>('/auth/signup', { method: 'POST', auth: false, body }),
  verifyEmail: (token: string) => request<void>('/auth/verify-email', { method: 'POST', auth: false, body: { token } }),
  resendVerification: (email: string) =>
    request<{ message: string }>('/auth/verify-email/resend', { method: 'POST', auth: false, body: { email } }),
  forgotPassword: (email: string) =>
    request<{ message: string }>('/auth/password/forgot', { method: 'POST', auth: false, body: { email } }),
  resetPassword: (token: string, password: string) =>
    request<void>('/auth/password/reset', { method: 'POST', auth: false, body: { token, password } }),
  logout: (refreshToken: string) =>
    request<void>('/auth/logout', { method: 'POST', auth: false, body: { refreshToken } }),

  // ---------- me ----------
  me: () => request<Me>('/me'),
  exportData: () => request<unknown>('/me/export'),
  erase: (password: string) => request<void>('/me/erase', { method: 'POST', body: { password } }),
  people: (userIds: string[]) => request<Person[]>('/people', { query: { userIds: userIds.join(',') } }),
  presence: () => request<string[]>('/presence'),

  // ---------- conversations ----------
  conversations: () => request<ConversationSummary[]>('/conversations'),
  requests: () => request<ConversationSummary[]>('/conversations/requests'),
  accept: (id: string) => request<Conversation>(`/conversations/${id}/accept`, { method: 'POST' }),
  decline: (id: string, block: boolean) =>
    request<void>(`/conversations/${id}/decline`, { method: 'POST', query: { block: block ? 'true' : undefined } }),
  conversation: (id: string) => request<Conversation>(`/conversations/${id}`),
  openDirect: (userId: string) => request<Conversation>('/conversations/direct', { method: 'POST', body: { userId } }),
  createGroup: (name: string, description: string | undefined, memberUserIds: string[]) =>
    request<Conversation>('/conversations/groups', { method: 'POST', body: { name, description, memberUserIds } }),
  rename: (id: string, name: string, description?: string) =>
    request<Conversation>(`/conversations/${id}`, { method: 'PUT', body: { name, description } }),
  addMember: (id: string, userId: string, shareHistory: boolean) =>
    request<void>(`/conversations/${id}/members`, { method: 'POST', body: { userId, shareHistory } }),
  removeMember: (id: string, userId: string) =>
    request<void>(`/conversations/${id}/members/${encodeURIComponent(userId)}`, { method: 'DELETE' }),
  read: (id: string, seq: number) => request<void>(`/conversations/${id}/read`, { method: 'PUT', body: { seq } }),
  delivered: (id: string, seq: number) =>
    request<void>(`/conversations/${id}/delivered`, { method: 'PUT', body: { seq } }),
  mute: (id: string, until: string | null) =>
    request<void>(`/conversations/${id}/mute`, { method: 'PUT', body: { until } }),
  typing: (id: string) => request<void>(`/conversations/${id}/typing`, { method: 'POST' }),

  // ---------- messages ----------
  newest: (id: string, size = 50) => request<MessagePage>(`/conversations/${id}/messages`, { query: { size } }),
  before: (id: string, before: number, size = 50) =>
    request<MessagePage>(`/conversations/${id}/messages`, { query: { before, size } }),
  after: (id: string, after: number, size = 100) =>
    request<MessagePage>(`/conversations/${id}/messages`, { query: { after, size } }),
  message: (messageId: string) => request<Message>(`/messages/${messageId}`),
  send: (id: string, body: {
    clientMessageId: string; type: 'TEXT' | 'IMAGE' | 'FILE'; body?: string;
    metadata?: { mentions: string[] }; attachmentIds?: string[];
  }) => request<Message>(`/conversations/${id}/messages`, { method: 'POST', body }),
  edit: (messageId: string, body: string) => request<Message>(`/messages/${messageId}`, { method: 'PUT', body: { body } }),
  remove: (messageId: string) => request<void>(`/messages/${messageId}`, { method: 'DELETE' }),
  react: (messageId: string, emoji: string) =>
    request<void>(`/messages/${messageId}/reactions/${encodeURIComponent(emoji)}`, { method: 'PUT' }),
  unreact: (messageId: string, emoji: string) =>
    request<void>(`/messages/${messageId}/reactions/${encodeURIComponent(emoji)}`, { method: 'DELETE' }),
  search: (q: string, conversationId?: string) =>
    request<Message[]>('/search/messages', { query: { q, conversationId } }),
  upload: (id: string, file: { uri: string; name: string; type: string } | Blob, name?: string) => {
    const form = new FormData();
    if (file instanceof Blob) {
      form.append('file', file, name);
    } else {
      // React Native's FormData takes a file by its uri
      form.append('file', file as unknown as Blob);
    }
    return request<Attachment>(`/conversations/${id}/attachments`, { method: 'POST', form });
  },

  // ---------- safety ----------
  blocks: () => request<string[]>('/blocks'),
  block: (userId: string) => request<void>(`/blocks/${encodeURIComponent(userId)}`, { method: 'PUT' }),
  unblock: (userId: string) => request<void>(`/blocks/${encodeURIComponent(userId)}`, { method: 'DELETE' }),
  report: (body: { conversationId: string; messageIds: string[]; category: ReportCategory; reason?: string }) =>
    request<{ id: string }>('/reports', { method: 'POST', body }),
};
