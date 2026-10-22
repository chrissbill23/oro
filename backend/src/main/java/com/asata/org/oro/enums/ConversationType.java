package com.asata.org.oro.enums;

/** direct (two identities, one conversation per pair and context) or group */
public enum ConversationType implements CodedEnum {
    DIRECT("direct"),
    GROUP("group");

    private final String code;

    ConversationType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static ConversationType of(String code) {
        return CodedEnum.of(ConversationType.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<ConversationType> {
        public DbConverter() {
            super(ConversationType.class);
        }
    }
}
