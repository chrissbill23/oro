package com.asata.org.oro.message.dto;

import com.asata.org.oro.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.OffsetDateTime;
import java.util.UUID;

/** a message; deleted: a tombstone, its body null and its metadata {} */
public record MessageDTO(UUID id, UUID conversationId, long seq, String senderUserId, String clientMessageId,
                         MessageType type, String body, @JsonRawValue String metadata, OffsetDateTime createdAt,
                         OffsetDateTime editedAt, boolean deleted) {
}
