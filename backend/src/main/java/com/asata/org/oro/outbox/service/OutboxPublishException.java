package com.asata.org.oro.outbox.service;

/** a publication failure whose code and message are safe to store (no content, no secret) */
public class OutboxPublishException extends RuntimeException {
    private final String code;

    public OutboxPublishException(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
