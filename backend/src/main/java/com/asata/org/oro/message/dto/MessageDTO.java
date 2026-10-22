package com.asata.org.oro.message.dto;

import com.asata.org.oro.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * a message; deleted: a tombstone, its body null and its metadata {}. reactions: per emoji, who gave it (user ids of
 * the product); attachments: its files
 */
public record MessageDTO(UUID id, UUID conversationId, long seq, String senderUserId, String clientMessageId,
                         MessageType type, String body, @JsonRawValue String metadata, OffsetDateTime createdAt,
                         OffsetDateTime editedAt, boolean deleted, List<ReactionDTO> reactions,
                         List<AttachmentDTO> attachments) {

    public record ReactionDTO(String emoji, List<String> userIds) {
    }

    /** available: uploaded and clean, it can be downloaded */
    public record AttachmentDTO(UUID id, String fileName, String mimeType, long sizeBytes, boolean available) {
    }
}
