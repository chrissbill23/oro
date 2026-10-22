package com.asata.org.oro.enums;

/** pending (upload URL given), uploaded (size and checksum checked) or rejected */
public enum UploadStatus implements CodedEnum {
    PENDING("pending"),
    UPLOADED("uploaded"),
    REJECTED("rejected");

    private final String code;

    UploadStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static UploadStatus of(String code) {
        return CodedEnum.of(UploadStatus.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<UploadStatus> {
        public DbConverter() {
            super(UploadStatus.class);
        }
    }
}
