/** Oro's API, as its JSON comes (backend README, API) */
export type ConversationType = 'DIRECT' | 'GROUP';
export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM';
export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface SystemEvent {
  event: 'member.joined' | 'member.left' | 'member.removed' | 'conversation.renamed';
  userId?: string;
  byUserId?: string;
  name?: string;
}

export interface LastMessage {
  id: string;
  seq: number;
  senderUserId?: string;
  type: MessageType;
  /** its first 200 characters */
  body?: string;
  metadata?: SystemEvent | { mentions?: string[] } | null;
  deleted: boolean;
  createdAt: string;
}

export interface ConversationSummary {
  id: string;
  type: ConversationType;
  name?: string;
  otherUserId?: string;
  memberCount: number;
  lastMessageSeq: number;
  lastMessageAt?: string;
  createdAt: string;
  unreadCount: number;
  mutedUntil?: string;
  role: MemberRole;
  lastMessage?: LastMessage;
  /** a request or an invitation: who asked */
  invitedByUserId?: string;
}

export interface Member {
  userId: string;
  role: MemberRole;
  sharedHistory: boolean;
  joinedAt: string;
  lastReadSeq: number;
  lastReadAt?: string;
  /** a request or an invitation not answered yet */
  pending: boolean;
}

export interface Conversation {
  id: string;
  type: ConversationType;
  name?: string;
  description?: string;
  createdBy?: string;
  lastMessageSeq: number;
  lastMessageAt?: string;
  createdAt: string;
  role: MemberRole;
  mutedUntil?: string;
  members: Member[];
}

export interface Reaction {
  emoji: string;
  userIds: string[];
}

export interface Attachment {
  id: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  /** scanned clean: downloadable */
  available: boolean;
}

export interface Message {
  id: string;
  conversationId: string;
  seq: number;
  senderUserId?: string;
  clientMessageId?: string;
  type: MessageType;
  body?: string;
  metadata?: (SystemEvent & { mentions?: string[] }) | null;
  createdAt: string;
  editedAt?: string;
  deleted: boolean;
  reactions: Reaction[];
  attachments: Attachment[];
}

export interface MessagePage {
  messages: Message[];
  nextBeforeSeq?: number | null;
}

export interface Me {
  identityId: string;
  product: 'ORO' | 'UMOJA';
  userId: string;
}

export interface Person {
  userId: string;
  givenName?: string;
  surname?: string;
  username: string;
}

export interface SignedIn {
  accessToken: string;
  expiresIn: number;
  userId: string;
  username: string;
  mustChangePassword: boolean;
  refreshToken?: string;
  refreshExpiresIn?: number;
}

export type ReportCategory = 'spam' | 'harassment' | 'illegal' | 'other';

/** the events of the stream (backend README, Live) */
export type LiveEvent =
  | { type: 'hello'; online: string[] }
  | { type: 'reconnect' }
  | { type: 'presence'; userId: string; online: boolean }
  | { type: 'typing'; conversationId: string; userId: string }
  | { type: 'read'; conversationId: string; userId: string; seq: number }
  | { type: 'message'; change: 'created' | 'edited' | 'deleted' | 'reaction'; conversationId: string; messageId: string; seq: number }
  | { type: 'conversation'; change: string; conversationId: string };
