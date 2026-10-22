package com.asata.org.oro.enums;

import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

/** an enum stored as its code (the values of the CHECK constraints of CREATE_ORO_TABLES.sql) */
public interface CodedEnum {

    String code();

    static <E extends Enum<E> & CodedEnum> E of(Class<E> type, String code) {
        return Arrays.stream(type.getEnumConstants()).filter(value -> value.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + code));
    }

    /** the JPA converter of a coded enum: each enum has its subclass, @Convert(converter = X.DbConverter.class) */
    abstract class Converter<E extends Enum<E> & CodedEnum> implements AttributeConverter<E, String> {
        private final Class<E> type;

        protected Converter(Class<E> type) {
            this.type = type;
        }

        @Override
        public String convertToDatabaseColumn(E value) {
            return value == null ? null : value.code();
        }

        @Override
        public E convertToEntityAttribute(String code) {
            return code == null ? null : of(type, code);
        }
    }
}
