package com.asata.org.oro.account;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * The access tokens of Oro's people: RS256 with Oro's key (its kid), issuer oro, audience oro, the Asata account as
 * subject, the session in sid, 15 minutes. Checked by Oro itself as the tokens of the product ORO (SecurityConfig).
 */
@Component
@RequiredArgsConstructor
public class AccountTokens {

    public static final String ISSUER = "oro";
    public static final String SESSION_CLAIM = "sid";

    private final AccountKeys keys;
    private final AccountProperties properties;

    public record AccessToken(String value, long expiresInSeconds) {
    }

    public AccessToken accessToken(UUID userId, String username, UUID sessionId) {
        Instant now = Instant.now();
        long ttl = properties.getAccessTokenTtl().toSeconds();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of("oro"))
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttl)))
                .claim("preferred_username", username)
                .claim(SESSION_CLAIM, sessionId.toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT)
                .keyID(keys.signingKey().getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(keys.signingKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("cannot sign an access token", e);
        }
        return new AccessToken(jwt.serialize(), ttl);
    }
}
