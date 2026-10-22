package com.asata.org.oro.enums;

/** personal (Oro) or organization (Umoja, with its organization) */
public enum ContextType implements CodedEnum {
    PERSONAL("personal"),
    ORGANIZATION("organization");

    private final String code;

    ContextType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static ContextType of(String code) {
        return CodedEnum.of(ContextType.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<ContextType> {
        public DbConverter() {
            super(ContextType.class);
        }
    }
}
