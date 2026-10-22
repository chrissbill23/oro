package com.asata.org.oro.enums;

/** active, suspended (keeps its conversations, can neither read nor send) or deactivated (final) */
public enum IdentityStatus implements CodedEnum {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    DEACTIVATED("deactivated");

    private final String code;

    IdentityStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static IdentityStatus of(String code) {
        return CodedEnum.of(IdentityStatus.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<IdentityStatus> {
        public DbConverter() {
            super(IdentityStatus.class);
        }
    }
}
