package com.asata.org.oro.outbox.service;

import com.asata.org.oro.enums.CodedEnum;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.context.DbSession;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Adds an event to the outbox in the transaction of its change (never alone), so that the event is committed with the
 * change or not at all: the transaction of a user (oro_app, the API's login) or of the system (oro_system, the
 * worker's login), with the connection of that transaction. The payload holds ids, numbers and codes only: no message
 * content, which the consumers read through an authorized path.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private static final Pattern KEY = Pattern.compile("[A-Za-z]{1,64}");

    private final DbSession session;

    public UUID add(Product product, String eventType, String aggregateType, UUID aggregateId, Map<String, ?> payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("an outbox event is added in the transaction of its change");
        }
        UUID id = UUID.randomUUID();
        // an insert only: the API may add to the outbox but not read it
        session.jdbc().update("""
                INSERT INTO oro_outbox_event(id, product_id, event_type, aggregate_type, aggregate_id, payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)""",
                id, product.code(), eventType, aggregateType, aggregateId, toJson(payload));
        return id;
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
