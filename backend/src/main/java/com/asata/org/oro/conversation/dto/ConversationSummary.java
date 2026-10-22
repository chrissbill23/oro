package com.asata.org.oro.conversation.dto;

import com.asata.org.oro.enums.ConversationType;
import com.asata.org.oro.enums.MemberRole;
import com.asata.org.oro.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A conversation of the list of a user. unreadCount = lastMessageSeq - its lastReadSeq (no count of rows).
 * otherUserId: the other one of a direct conversation; teamId: the team of a team chat (Umoja); role: the caller's.
 * lastMessage: the last one the caller may read, null when there is none.
 */
public record ConversationSummary(UUID id, ConversationType type, String name, UUID teamId, String otherUserId,
                                  long memberCount, long lastMessageSeq, OffsetDateTime lastMessageAt,
                                  OffsetDateTime createdAt, long unreadCount, OffsetDateTime mutedUntil, MemberRole role,
                                  LastMessage lastMessage) {

    /** the preview of a message: its first 200 characters; metadata: of a system message only (what happened) */
    public record LastMessage(UUID id, long seq, String senderUserId, MessageType type, String body,
                              @JsonRawValue String metadata, boolean deleted, OffsetDateTime createdAt) {
    }
}
