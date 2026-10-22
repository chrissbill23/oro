package com.asata.org.oro.conversation.dto;

import com.asata.org.oro.enums.ContextType;
import com.asata.org.oro.enums.ConversationType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** a conversation and its active members (user ids of the product) */
public record ConversationDTO(UUID id, ConversationType type, ContextType context, UUID organizationId, String name,
                              String description, String createdBy, long lastMessageSeq, OffsetDateTime lastMessageAt,
                              OffsetDateTime createdAt, List<MemberDTO> members) {
}
