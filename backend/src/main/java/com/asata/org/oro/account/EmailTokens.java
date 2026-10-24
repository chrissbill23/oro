package com.asata.org.oro.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The links sent by email (asata_user_email_token): a random token of 256 bits in the link only, its SHA-256 stored;
 * used once, before its expiry, for the address it was sent to. Called inside the transactions of {@link AccountDb}.
 */
@Component
public class EmailTokens {

    public static final String VERIFY_EMAIL = "verify-email";
    public static final String RESET_PASSWORD = "reset-password";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** a new token for the account and the address; the token, to put in the link */
    public String issue(JdbcTemplate jdbc, UUID userId, String purpose, String email, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                INSERT INTO asata_user_email_token(user_id, purpose, token_hash, email, expires_at)
                VALUES (?, ?, ?, ?, ?)""", userId, purpose, hash(token), email, now().plus(ttl));
        return token;
    }

    /** the account and the address of a valid token of this purpose, now used; empty when unknown, used or expired */
    public Optional<Consumed> consume(JdbcTemplate jdbc, String token, String purpose) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return Optional.empty();
        }
        List<Consumed> found = jdbc.query("""
                        UPDATE asata_user_email_token SET used_at = now()
                        WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > now()
                        RETURNING user_id, email""",
                (rs, i) -> new Consumed(rs.getObject("user_id", UUID.class), rs.getString("email")), hash(token), purpose);
        return found.stream().findFirst();
    }

    public record Consumed(UUID userId, String email) {
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
