package com.asata.org.oro.enums;

/** pending, processing (claimed by a worker, with a lease), processed or failed (after the last attempt) */
public enum OutboxStatus implements CodedEnum {
    PENDING("pending"),
    PROCESSING("processing"),
    PROCESSED("processed"),
    FAILED("failed");

    private final String code;

    OutboxStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static OutboxStatus of(String code) {
        return CodedEnum.of(OutboxStatus.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<OutboxStatus> {
        public DbConverter() {
            super(OutboxStatus.class);
        }
    }
}
