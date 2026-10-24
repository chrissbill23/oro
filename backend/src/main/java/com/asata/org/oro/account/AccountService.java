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
    private final EmailTokens emailTokens;
    private final Mailer mailer;

    static final String CHECK_YOUR_EMAIL = "Check your email to finish creating your account";
    static final String VERIFY_FIRST = "Verify your email address first: open the link we sent you, or ask for a new one";
    private static final java.util.regex.Pattern USERNAME = java.util.regex.Pattern.compile("[A-Za-z0-9._-]{3,64}");
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]+");

    /** an email to send once the transaction committed */
    private record Outgoing(String to, String subject, String text) {
    }

    /** signed in: the access token, and the session whose refresh token goes into the cookie */
    public record SignedIn(AccountTokens.AccessToken accessToken, AccountSessions.Session session, UUID userId,
                           String username, boolean mustChangePassword) {
    }

    private record Account(UUID id, String username, boolean active, String passwordHash, int failedAttempts,
                           OffsetDateTime lockedUntil, boolean mustChangePassword, boolean emailVerified) {
    }

    public SignedIn login(String login, String password, boolean remember, String userAgent) {
        if (login == null || login.isBlank() || login.length() > 255 || password == null || password.length() > 1024) {
            throw new Unauthorized(INVALID_LOGIN);
        }
        return committed(jdbc -> {
            OffsetDateTime now = now();
            List<Account> found = jdbc.query("""
                            SELECT u.id, u.username, u.is_active AND u.deleted_at IS NULL AS active, c.password_hash,
                                   c.failed_attempts, c.locked_until, c.must_change_password, u.email_verified
                            FROM asata_user u JOIN asata_user_credential c ON c.user_id = u.id
                            WHERE lower(u.email) = lower(?) OR lower(u.username) = lower(?)
                            FOR UPDATE OF c""",
                    (rs, i) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getBoolean("active"),
                            rs.getString("password_hash"), rs.getInt("failed_attempts"),
                            rs.getObject("locked_until", OffsetDateTime.class), rs.getBoolean("must_change_password"),
                            rs.getBoolean("email_verified")),
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
            // the right password of an address never verified: told (the password proves the account), no session
            if (!account.emailVerified()) {
                log.info("sign-in refused: account {} has not verified its email", account.id());
                throw new com.asata.org.oro.exception.Forbidden(VERIFY_FIRST);
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
                            null, 0, null, rs.getBoolean("must_change_password"), true),
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

    /**
     * A new Asata account, its address to verify. The same answer whether the address has an account or not: a new
     * one gets the link to verify it, a known one a notice (someone tried to sign up with it). A username taken is
     * refused (usernames are shown to others: no secret).
     */
    public void signUp(String email, String username, String password, String givenName, String surname) {
        String address = email == null ? "" : email.strip();
        if (address.length() > 255 || !EMAIL.matcher(address).matches()) {
            throw new IllegalArgumentException("An email address");
        }
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("A username of 3 to 64 letters, digits, dots, dashes or underscores");
        }
        requireGoodPassword(password, address, username);
        String name = blankToNull(givenName, 255);
        String familyName = blankToNull(surname, 255);
        // hashed whatever comes next: the time of the answer tells nothing
        String hash = passwordEncoder.encode(password);
        Outgoing mail;
        try {
            mail = db.inTransaction(jdbc -> {
                List<UUID> known = jdbc.queryForList("SELECT id FROM asata_user WHERE lower(email) = lower(?)", UUID.class, address);
                if (!known.isEmpty()) {
                    log.info("sign-up with the address of account {}: a notice sent", known.getFirst());
                    return alreadyRegistered(address);
                }
                if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM asata_user WHERE lower(username) = lower(?))",
                        Boolean.class, username))) {
                    throw new com.asata.org.oro.exception.Conflict("This username is taken");
                }
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO asata_user(id, username, email) VALUES (?, ?, ?)", id, username, address);
                jdbc.update("INSERT INTO asata_user_credential(user_id, password_hash) VALUES (?, ?)", id, hash);
                if (name != null || familyName != null) {
                    jdbc.update("INSERT INTO asata_user_personal_info(user_id, given_name, surname) VALUES (?, ?, ?)", id, name, familyName);
                }
                log.info("account {} created, its address to verify", id);
                return verification(jdbc, id, address);
            });
        } catch (org.springframework.dao.DuplicateKeyException race) {
            // the same address or username signed up at the same moment
            if (String.valueOf(race.getMessage()).contains("username")) {
                throw new com.asata.org.oro.exception.Conflict("This username is taken");
            }
            mail = alreadyRegistered(address);
        }
        send(mail);
    }

    /** the address of the link's account verified, if it is still its address */
    public void verifyEmail(String token) {
        boolean verified = Boolean.TRUE.equals(db.inTransaction(jdbc -> emailTokens.consume(jdbc, token, EmailTokens.VERIFY_EMAIL)
                .map(consumed -> jdbc.update("UPDATE asata_user SET email_verified = true WHERE id = ? AND lower(email) = lower(?)",
                        consumed.userId(), consumed.email()) == 1)
                .orElse(false)));
        if (!verified) {
            throw new IllegalArgumentException("This link is not valid any more: ask for a new one");
        }
    }

    /** a new link to verify the address, if it is the address of an account not verified yet; the same answer always */
    public void resendVerification(String email) {
        String address = email == null ? "" : email.strip();
        if (address.isEmpty() || address.length() > 255) {
            return;
        }
        Outgoing mail = db.inTransaction(jdbc -> {
            List<UUID> waiting = jdbc.queryForList("""
                    SELECT id FROM asata_user WHERE lower(email) = lower(?) AND NOT email_verified AND is_active AND deleted_at IS NULL""",
                    UUID.class, address);
            return waiting.isEmpty() ? null : verification(jdbc, waiting.getFirst(), address);
        });
        if (mail != null) {
            send(mail);
        }
    }

    private Outgoing verification(JdbcTemplate jdbc, UUID userId, String address) {
        String token = emailTokens.issue(jdbc, userId, EmailTokens.VERIFY_EMAIL, address, properties.getVerifyTtl());
        String link = properties.getLinkBase() + "/verify-email?token=" + token;
        return new Outgoing(address, "Verify your email address for Oro", """
                Welcome to Oro.

                To finish creating your account, open this link within %d hours:

                %s

                If you did not sign up, ignore this email: the account stays unused without it.
                """.formatted(properties.getVerifyTtl().toHours(), link));
    }

    private Outgoing alreadyRegistered(String address) {
        return new Outgoing(address, "Your Asata account and Oro", """
                Someone, maybe you, tried to create an Oro account with this address.

                It already has an Asata account (the one of Umoja): sign in to Oro with it. If you forgot its password,
                reset it from the sign-in page.

                If it was not you, ignore this email: nothing changed.
                """);
    }

    private void send(Outgoing mail) {
        try {
            mailer.send(mail.to(), mail.subject(), mail.text());
        } catch (RuntimeException e) {
            // never the address nor the link in the log; the person asks for a new link
            log.warn("an email of the accounts could not be sent: {}", e.getClass().getName());
        }
    }

    private void requireGoodPassword(String password, String email, String username) {
        if (password == null || password.length() < properties.getMinPasswordLength()
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A password of " + properties.getMinPasswordLength() + " characters at least, 72 bytes at most");
        }
        if (password.equalsIgnoreCase(email) || password.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("A password that is not the email nor the username");
        }
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > max) {
            throw new IllegalArgumentException("A name of " + max + " characters at most");
        }
        return v;
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
