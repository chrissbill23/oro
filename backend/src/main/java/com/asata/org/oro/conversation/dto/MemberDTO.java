package com.asata.org.oro.conversation.dto;

import com.asata.org.oro.enums.MemberRole;

import java.time.OffsetDateTime;

/** sharedHistory: the member reads the messages sent before it joined */
public record MemberDTO(String userId, MemberRole role, boolean sharedHistory, OffsetDateTime joinedAt) {
}
