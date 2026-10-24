package com.asata.org.oro.account;

import com.asata.org.oro.exception.Unauthorized;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;

/**
 * The sessions of Oro: refresh tokens in asata_user_refresh_token (shared with Umoja and Mokili), issuer oro, stored as
 * the SHA-256 of "oro:" + the token. The others look a token up by the SHA-256 of the token alone: they never find
 * Oro's, nor Oro theirs. A token is replaced at each use (same family: the session); a token already replaced, used
 * again, means it was stolen: the whole session is revoked, unless a concurrent request just replaced it. Called inside
 * the transactions of {@link AccountDb}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSessions {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DOMAIN = "oro:";

    private final AccountProperties properties;

    /** sessionId: the family; token: the refresh token, given once (only its hash is kept) */
    public record Session(UUID userId, UUID sessionId, String token, Duration ttl, boolean remember) {
    }

    public Session open(JdbcTemplate jdbc, UUID userId, boolean remember, String userAgent) {
        Duration ttl = remember ? properties.getRememberTtl() : properties.getSessionTtl();
        UUID sessionId = UUID.randomUUID();
        return new Session(userId, sessionId, save(jdbc, userId, sessionId, ttl, userAgent), ttl, remember);
    }

    private record Stored(long id, UUID userId, UUID familyId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    }

    public Session rotate(JdbcTemplate jdbc, String token, String userAgent) {
        OffsetDateTime now = now();
        Stored stored = find(jdbc, token).orElseThrow(() -> new Unauthorized("Invalid session, please sign in again"));
        boolean remember = Duration.between(now, stored.expiresAt()).compareTo(properties.getSessionTtl()) > 0;
        Duration ttl = remember ? properties.getRememberTtl() : properties.getSessionTtl();
        if (stored.revokedAt() != null) {
            if (concurrentUse(jdbc, stored, now)) {
                return new Session(stored.userId(), stored.familyId(), save(jdbc, stored.userId(), stored.familyId(), ttl, userAgent), ttl, remember);
            }
            int revoked = revokeFamily(jdbc, stored.familyId(), now);
            log.warn("reuse of a replaced refresh token of user {}: {} token(s) of the session revoked", stored.userId(), revoked);
            throw new Unauthorized("Invalid session, please sign in again");
        }
        if (stored.expiresAt().isBefore(now)) {
            throw new Unauthorized("Session expired, please sign in again");
        }
        jdbc.update("UPDATE asata_user_refresh_token SET revoked_at = ? WHERE id = ?", now, stored.id());
        return new Session(stored.userId(), stored.familyId(), save(jdbc, stored.userId(), stored.familyId(), ttl, userAgent), ttl, remember);
    }

    /** the session of this token closed (sign-out); nothing for an unknown token */
    public void close(JdbcTemplate jdbc, String token) {
        find(jdbc, token).ifPresent(stored -> revokeFamily(jdbc, stored.familyId(), now()));
    }

    /** every session of Oro of the user closed; the number of tokens revoked */
    public int closeAll(JdbcTemplate jdbc, UUID userId) {
        return jdbc.update("UPDATE asata_user_refresh_token SET revoked_at = ? WHERE user_id = ? AND issuer = 'oro' AND revoked_at IS NULL",
                now(), userId);
    }

    private java.util.Optional<Stored> find(JdbcTemplate jdbc, String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return java.util.Optional.empty();
        }
        List<Stored> found = jdbc.query("""
                        SELECT id, user_id, family_id, expires_at, revoked_at FROM asata_user_refresh_token
                        WHERE token_hash = ? AND issuer = 'oro'""",
                (rs, i) -> new Stored(rs.getLong("id"), rs.getObject("user_id", UUID.class), rs.getObject("family_id", UUID.class),
                        rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("revoked_at", OffsetDateTime.class)),
                hash(token));
        return found.stream().findFirst();
    }

    /** replaced, not signed out (the session still has a live token), within the grace */
    private boolean concurrentUse(JdbcTemplate jdbc, Stored stored, OffsetDateTime now) {
        return stored.revokedAt().plus(properties.getReuseGrace()).isAfter(now) && stored.expiresAt().isAfter(now)
                && Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM asata_user_refresh_token
                                       WHERE family_id = ? AND issuer = 'oro' AND revoked_at IS NULL AND expires_at > ?)""",
                Boolean.class, stored.familyId(), now));
    }

    private static int revokeFamily(JdbcTemplate jdbc, UUID familyId, OffsetDateTime now) {
        return jdbc.update("UPDATE asata_user_refresh_token SET revoked_at = ? WHERE family_id = ? AND issuer = 'oro' AND revoked_at IS NULL",
                now, familyId);
    }

    private static String save(JdbcTemplate jdbc, UUID userId, UUID familyId, Duration ttl, String userAgent) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                        INSERT INTO asata_user_refresh_token(user_id, token_hash, family_id, expires_at, user_agent, issuer)
                        VALUES (?, ?, ?, ?, ?, 'oro')""",
                userId, hash(token), familyId, now().plus(ttl),
                userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512)));
        return token;
    }

    /** the SHA-256 of "oro:" + the token: never the hash Umoja or Mokili compute for the same token */
    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((DOMAIN + token).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
