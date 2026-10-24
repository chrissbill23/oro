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
}
