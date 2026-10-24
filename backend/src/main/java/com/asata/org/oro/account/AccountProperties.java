package com.asata.org.oro.account;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** oro.accounts.*: the sign-in of Oro into Asata accounts, the tokens it signs */
@Getter
@Setter
@ConfigurationProperties(prefix = "oro.accounts")
public class AccountProperties {
    /** the RSA private key that signs the access tokens, PKCS#8 base64 on one line; blank: a new one at each start */
    private String signingKey;
    /** former signing keys (same form), still published and accepted during a rotation */
    private List<String> previousKeys = List.of();
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    /** a session without "keep me signed in": it ends with the browser, or after this */
    private Duration sessionTtl = Duration.ofHours(12);
    private Duration rememberTtl = Duration.ofDays(30);
    /** a refresh token just replaced stays usable this long (two tabs refreshing together) */
    private Duration reuseGrace = Duration.ofSeconds(30);
    private boolean cookieSecure = true;
    private String cookieName = "oro_refresh";
    private int maxFailedAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);
    /** where the links of the emails lead: the Oro app, which posts the token back (/verify-email?token=...) */
    private String linkBase = "http://localhost:4300";
    /** the sender of the emails */
    private String mailFrom = "Oro <no-reply@oro.localhost>";
    /** how long a link to verify an address stays valid */
    private Duration verifyTtl = Duration.ofHours(24);
    /** how long a link to choose a new password stays valid */
    private Duration resetTtl = Duration.ofHours(1);
    /** the length of a new password, at least; at most 128 (bcrypt reads 72 bytes, the rest is refused, not cut) */
    private int minPasswordLength = 10;
}
