package com.asata.org.oro.message.dto;

import com.asata.org.oro.attachment.entity.Attachment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** what goes with messages: their reactions (per emoji, in order) and their attachments, by message id */
public record MessageExtras(Map<UUID, List<MessageDTO.ReactionDTO>> reactions, Map<UUID, List<Attachment>> attachments) {

    public static final MessageExtras NONE = new MessageExtras(Map.of(), Map.of());
}
