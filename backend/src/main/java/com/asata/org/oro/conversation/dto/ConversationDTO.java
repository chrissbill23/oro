package com.asata.org.oro.conversation.dto;

import com.asata.org.oro.enums.ContextType;
import com.asata.org.oro.enums.ConversationType;
import com.asata.org.oro.enums.MemberRole;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * a conversation and its active members (user ids of the product). teamId: the team of a team chat (Umoja), whose
 * name and members follow the team. role, mutedUntil: the caller's.
 */
public record ConversationDTO(UUID id, ConversationType type, ContextType context, UUID organizationId, String name,
                              String description, UUID teamId, String createdBy, long lastMessageSeq,
                              OffsetDateTime lastMessageAt, OffsetDateTime createdAt, MemberRole role,
                              OffsetDateTime mutedUntil, List<MemberDTO> members) {
}
