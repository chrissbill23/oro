package com.asata.org.oro.outbox.service;

import com.asata.org.oro.enums.CodedEnum;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.outbox.entity.OutboxEvent;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Adds an event to the outbox in the transaction of its change (MANDATORY: never alone), so that the event is
 * committed with the change or not at all. The payload holds ids, numbers and codes only: no message content, which
 * the consumers read through an authorized path.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private static final Pattern KEY = Pattern.compile("[A-Za-z]{1,64}");

    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID add(Product product, String eventType, String aggregateType, UUID aggregateId, Map<String, ?> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setProduct(product);
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(toJson(payload));
        event.setCreatedAt(OffsetDateTime.now());
        // persist, never save(): the API may insert into the outbox but not read it
        em.persist(event);
        return event.getId();
    }

    static String toJson(Map<String, ?> payload) {
        return payload.entrySet().stream().map(entry -> {
            if (!KEY.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("Invalid payload key: " + entry.getKey());
            }
            return "\"" + entry.getKey() + "\":" + toJsonValue(entry.getValue());
        }).collect(Collectors.joining(",", "{", "}"));
    }

    private static String toJsonValue(Object value) {
        return switch (value) {
            case null -> "null";
            case Number number -> number.toString();
            case Boolean bool -> bool.toString();
            case UUID uuid -> "\"" + uuid + "\"";
            case CodedEnum coded -> "\"" + coded.code() + "\"";
            default -> throw new IllegalArgumentException("Outbox payloads hold ids, numbers and codes only");
        };
    }
}
