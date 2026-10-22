package com.asata.org.oro.enums;

/** the kind of a message; system messages have no sender */
public enum MessageType implements CodedEnum {
    TEXT("text"),
    IMAGE("image"),
    FILE("file"),
    SYSTEM("system");

    private final String code;

    MessageType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static MessageType of(String code) {
        return CodedEnum.of(MessageType.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<MessageType> {
        public DbConverter() {
            super(MessageType.class);
        }
    }
}
