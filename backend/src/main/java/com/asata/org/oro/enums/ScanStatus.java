package com.asata.org.oro.enums;

/** the malware scan of an attachment: only a clean file is downloaded */
public enum ScanStatus implements CodedEnum {
    PENDING("pending"),
    CLEAN("clean"),
    INFECTED("infected"),
    FAILED("failed");

    private final String code;

    ScanStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static ScanStatus of(String code) {
        return CodedEnum.of(ScanStatus.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<ScanStatus> {
        public DbConverter() {
            super(ScanStatus.class);
        }
    }
}
