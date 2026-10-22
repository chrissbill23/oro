package com.asata.org.oro.enums;

/** why a membership ended */
public enum LeftReason implements CodedEnum {
    LEFT("left"),
    REMOVED("removed"),
    ACCESS_REVOKED("access_revoked"),
    DEACTIVATED("deactivated");

    private final String code;

    LeftReason(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static LeftReason of(String code) {
        return CodedEnum.of(LeftReason.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<LeftReason> {
        public DbConverter() {
            super(LeftReason.class);
        }
    }
}
