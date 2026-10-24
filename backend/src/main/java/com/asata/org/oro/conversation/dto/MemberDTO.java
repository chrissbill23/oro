package com.asata.org.oro.conversation.dto;

import com.asata.org.oro.enums.MemberRole;

import java.time.OffsetDateTime;

/**
 * sharedHistory: the member reads the messages sent before it joined. lastReadSeq, lastReadAt: how far it has read,
 * and when it last read on (the "Seen" of the messages). pending: invited (or asked), not accepted yet (Oro)
 */
public record MemberDTO(String userId, MemberRole role, boolean sharedHistory, OffsetDateTime joinedAt,
                        long lastReadSeq, OffsetDateTime lastReadAt, boolean pending) {
}
