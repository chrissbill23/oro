package com.asata.org.oro.message.dto;

import com.asata.org.oro.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * clientMessageId: generated once by the client for this message and sent again on every retry (idempotency).
 * metadata: an optional JSON object. attachmentIds: the caller's uploads in this conversation.
 */
public record SendMessageRequest(@NotNull @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}") String clientMessageId,
                                 @NotNull MessageType type, @Size(max = 16000) String body, JsonNode metadata,
                                 @Size(max = 10) List<UUID> attachmentIds) {
}
