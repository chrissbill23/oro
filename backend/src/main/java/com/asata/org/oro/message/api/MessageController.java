package com.asata.org.oro.message.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.message.dto.*;
import com.asata.org.oro.message.entity.Message;
import com.asata.org.oro.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class MessageController implements MessageApi {

    private final MessageService messages;
    private final IdentityService identities;

    @Override
    public MessagePageDTO page(Actor actor, UUID conversationId, Long before, Long after, int size) {
        if (before != null && after != null) {
            throw new IllegalArgumentException("before or after, not both");
        }
        if (after != null) {
            return new MessagePageDTO(toDtos(actor, messages.after(actor, conversationId, after, size)), null);
        }
        MessagePage page = messages.page(actor, conversationId, before, size);
        return new MessagePageDTO(toDtos(actor, page.messages()), page.nextBeforeSeq());
    }

    @Override
    public ResponseEntity<MessageDTO> send(Actor actor, UUID conversationId, SendMessageRequest request) {
        if (request.metadata() != null && !request.metadata().isObject()) {
            throw new IllegalArgumentException("metadata is a JSON object");
        }
        SendResult result = messages.send(actor, conversationId, new SendMessage(request.clientMessageId(), request.type(),
                request.body(), request.metadata() == null ? null : request.metadata().toString(),
                request.attachmentIds() == null ? List.of() : request.attachmentIds()));
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(toDtos(actor, List.of(result.message())).getFirst());
    }

    @Override
    public MessageDTO edit(Actor actor, UUID messageId, EditMessageRequest request) {
        return toDtos(actor, List.of(messages.edit(actor, messageId, request.body()))).getFirst();
    }

    @Override
    public void delete(Actor actor, UUID messageId) {
        messages.delete(actor, messageId);
    }

    private List<MessageDTO> toDtos(Actor actor, List<Message> page) {
        Set<UUID> senders = new HashSet<>();
        page.forEach(message -> {
            if (message.getSenderId() != null) {
                senders.add(message.getSenderId());
            }
        });
        Map<UUID, String> userIds = senders.isEmpty() ? Map.of() : identities.externalIds(actor.product(), senders);
        return page.stream().map(message -> new MessageDTO(message.getId(), message.getConversationId(), message.getSeq(),
                message.getSenderId() == null ? null : userIds.get(message.getSenderId()), message.getClientMessageId(),
                message.getType(), message.getBody(), message.getMetadata(), message.getCreatedAt(), message.getEditedAt(),
                message.isDeleted())).toList();
    }
}
