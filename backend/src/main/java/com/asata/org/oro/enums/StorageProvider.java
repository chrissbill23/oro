package com.asata.org.oro.enums;

/** the object storage holding the file of an attachment */
public enum StorageProvider implements CodedEnum {
    S3("s3"),
    GCS("gcs"),
    AZURE_BLOB("azure_blob"),
    LOCAL("local");

    private final String code;

    StorageProvider(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static StorageProvider of(String code) {
        return CodedEnum.of(StorageProvider.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<StorageProvider> {
        public DbConverter() {
            super(StorageProvider.class);
        }
    }
}
