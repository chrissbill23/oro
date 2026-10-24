package com.asata.org.oro.account;

import com.asata.org.oro.exception.Unauthorized;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The sign-in of Oro's people into their Asata account (asata_user, asata_user_credential: shared with Umoja and
 * Mokili, the same password, the same counter of wrong attempts). The same answer whatever went wrong, and the same
 * time: an unknown login checks a dummy hash. Logs name the account by its id, never by the login typed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /** the same answer whatever went wrong: unknown login, wrong password, locked or disabled account */
    static final String INVALID_LOGIN = "Invalid email or password, or account temporarily locked";
    /** checked when the account does not exist, so that the time of the answer does not tell */
    private static final String DUMMY_HASH = "{bcrypt}$2a$10$n/9UAr1C/PHHNWIJBcN62egbUVVFze/rivfGMfL1GOpefmVMashQO";

    private final AccountDb db;
    private final AccountSessions sessions;
    private final AccountTokens tokens;
    private final AccountProperties properties;
    private final PasswordEncoder passwordEncoder;

    /** signed in: the access token, and the session whose refresh token goes into the cookie */
    public record SignedIn(AccountTokens.AccessToken accessToken, AccountSessions.Session session, UUID userId,
                           String username, boolean mustChangePassword) {
    }

    private record Account(UUID id, String username, boolean active, String passwordHash, int failedAttempts,
                           OffsetDateTime lockedUntil, boolean mustChangePassword) {
    }

    public SignedIn login(String login, String password, boolean remember, String userAgent) {
        if (login == null || login.isBlank() || login.length() > 255 || password == null || password.length() > 1024) {
            throw new Unauthorized(INVALID_LOGIN);
        }
        return committed(jdbc -> {
            OffsetDateTime now = now();
            List<Account> found = jdbc.query("""
                            SELECT u.id, u.username, u.is_active AND u.deleted_at IS NULL AS active, c.password_hash,
                                   c.failed_attempts, c.locked_until, c.must_change_password
                            FROM asata_user u JOIN asata_user_credential c ON c.user_id = u.id
                            WHERE lower(u.email) = lower(?) OR lower(u.username) = lower(?)
                            FOR UPDATE OF c""",
                    (rs, i) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("active"),
                            rs.getString("password_hash"), rs.getInt("failed_attempts"),
                            rs.getObject("locked_until", OffsetDateTime.class), rs.getBoolean("must_change_password")),
                    login.trim(), login.trim());
            if (found.isEmpty()) {
                passwordEncoder.matches(password, DUMMY_HASH);
                log.info("sign-in refused: unknown login, or no password");
                throw new Unauthorized(INVALID_LOGIN);
            }
            Account account = found.getFirst();
            if (account.lockedUntil() != null && account.lockedUntil().isAfter(now)) {
                passwordEncoder.matches(password, DUMMY_HASH);
                log.info("sign-in refused: account {} locked until {}", account.id(), account.lockedUntil());
                throw new Unauthorized(INVALID_LOGIN);
            }
            if (!passwordEncoder.matches(password, account.passwordHash())) {
                failedAttempt(jdbc, account, now);
                throw new Unauthorized(INVALID_LOGIN);
            }
            if (!account.active()) {
                log.info("sign-in refused: account {} inactive or deleted", account.id());
                throw new Unauthorized(INVALID_LOGIN);
            }
            jdbc.update("UPDATE asata_user_credential SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?", account.id());
            jdbc.update("UPDATE asata_user SET last_login_at = ? WHERE id = ?", now, account.id());
            AccountSessions.Session session = sessions.open(jdbc, account.id(), remember, userAgent);
            log.info("account {} signed in", account.id());
            return new SignedIn(tokens.accessToken(account.id(), account.username(), session.sessionId()), session,
                    account.id(), account.username(), account.mustChangePassword());
        });
    }

    /** a new access token and a new refresh token for the session of this one; the account must still be able to sign in */
    public SignedIn refresh(String refreshToken, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new Unauthorized("No session, please sign in");
        }
        return committed(jdbc -> {
            AccountSessions.Session session = sessions.rotate(jdbc, refreshToken, userAgent);
            List<Account> found = jdbc.query("""
                            SELECT u.id, u.username, u.is_active AND u.deleted_at IS NULL AS active, c.must_change_password
                            FROM asata_user u LEFT JOIN asata_user_credential c ON c.user_id = u.id WHERE u.id = ?""",
                    (rs, i) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("active"),
                            null, 0, null, rs.getBoolean("must_change_password")),
                    session.userId());
            if (found.isEmpty() || !found.getFirst().active()) {
                sessions.close(jdbc, session.token());
                log.info("session of account {} closed: the account is inactive or deleted", session.userId());
                throw new Unauthorized("Account disabled");
            }
            Account account = found.getFirst();
            return new SignedIn(tokens.accessToken(account.id(), account.username(), session.sessionId()), session,
                    account.id(), account.username(), account.mustChangePassword());
        });
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            db.inTransaction(jdbc -> {
                sessions.close(jdbc, refreshToken);
                return null;
            });
        }
    }

    private void failedAttempt(JdbcTemplate jdbc, Account account, OffsetDateTime now) {
        int attempts = account.failedAttempts() + 1;
        if (attempts >= properties.getMaxFailedAttempts()) {
            jdbc.update("UPDATE asata_user_credential SET failed_attempts = 0, locked_until = ? WHERE user_id = ?",
                    now.plus(properties.getLockDuration()), account.id());
            log.warn("account {} locked after {} wrong passwords", account.id(), attempts);
        } else {
            jdbc.update("UPDATE asata_user_credential SET failed_attempts = ? WHERE user_id = ?", attempts, account.id());
        }
    }

    /**
     * the work in a transaction that commits even when it refuses (the wrong attempt counted, the stolen session
     * revoked), the refusal thrown after
     */
    private <T> T committed(Function<JdbcTemplate, T> work) {
        Object result = db.inTransaction(jdbc -> {
            try {
                return work.apply(jdbc);
            } catch (Unauthorized refused) {
                return refused;
            }
        });
        if (result instanceof Unauthorized refused) {
            throw refused;
        }
        @SuppressWarnings("unchecked")
        T done = (T) result;
        return done;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
