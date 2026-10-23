package com.asata.org.oro.message.dto;

import com.asata.org.oro.enums.MessageType;

import java.util.List;
import java.util.UUID;

/**
 * A message to send. clientMessageId: the idempotency key the client generates once per message and repeats on each
 * retry (8 to 64 characters among A-Z a-z 0-9 . _ : -). mentions: the user ids (in the product) of members of the
 * conversation, the only metadata of a message of a user (MessageMetadata). attachmentIds: uploads of the sender in the
 * conversation, not attached yet.
 */
public record SendMessage(String clientMessageId, MessageType type, String body, List<String> mentions, List<UUID> attachmentIds) {

    public SendMessage {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }

    public static SendMessage text(String clientMessageId, String body) {
        return new SendMessage(clientMessageId, MessageType.TEXT, body, List.of(), List.of());
    }
}
