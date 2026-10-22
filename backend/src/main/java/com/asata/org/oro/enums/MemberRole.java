package com.asata.org.oro.enums;

/** the role of a member in a conversation: the owner and the admins manage its members */
public enum MemberRole implements CodedEnum {
    OWNER("owner"),
    ADMIN("admin"),
    MEMBER("member");

    private final String code;

    MemberRole(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static MemberRole of(String code) {
        return CodedEnum.of(MemberRole.class, code);
    }

    @jakarta.persistence.Converter
    public static class DbConverter extends CodedEnum.Converter<MemberRole> {
        public DbConverter() {
            super(MemberRole.class);
        }
    }
}
