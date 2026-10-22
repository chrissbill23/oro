package com.asata.org.oro.conversation.dto;

import com.asata.org.oro.enums.ConversationType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** a conversation of the list of a user: unreadCount = lastMessageSeq - its lastReadSeq (no count of rows) */
public record ConversationSummary(UUID id, ConversationType type, String name, UUID organizationId,
                                  long lastMessageSeq, OffsetDateTime lastMessageAt, long unreadCount) {
}
