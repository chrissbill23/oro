package com.asata.org.oro.enums;

/** the product an identity, a conversation or an event belongs to: two account systems that never share an identity */
public enum Product implements CodedEnum {
    ORO("ORO"),
    UMOJA("UMOJA");

    private final String code;

    Product(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static Product of(String code) {
        return CodedEnum.of(Product.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<Product> {
        public DbConverter() {
            super(Product.class);
        }
    }
}
