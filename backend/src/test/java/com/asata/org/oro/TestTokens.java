package com.asata.org.oro;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The tokens of the tests, signed as the products sign theirs (RS256, a kid, the audience oro). The keys are new for
 * each run: Oro trusts the public ones of ORO and UMOJA (PostgresIntegrationTest), never OTHER.
 */
public final class TestTokens {

    public static final RSAKey ORO = generate();
    public static final RSAKey UMOJA = generate();
    /** a key nobody trusts */
    public static final RSAKey OTHER = generate();

    private TestTokens() {
    }

    /** the public JWK set of the key, as a product publishes it */
    /** the private key as oro.accounts.signing-key takes it: PKCS#8, base64 */
    public static String privateKeyPkcs8(RSAKey key) {
        try {
            return java.util.Base64.getEncoder().encodeToString(key.toRSAPrivateKey().getEncoded());
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String publicJwkSet(RSAKey... keys) {
        return new JWKSet(java.util.Arrays.stream(keys).map(key -> (com.nimbusds.jose.jwk.JWK) key.toPublicJWK()).toList())
                .toString();
    }

    /** the claims of a valid token: issuer, subject, the audience oro, an expiry */
    public static JwtClaimsSet.Builder claims(String issuer, String subject, Instant expiresAt) {
        return JwtClaimsSet.builder().issuer(issuer).subject(subject).audience(List.of(issuer, "oro"))
                .issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES)).expiresAt(expiresAt);
    }

    public static String sign(RSAKey key, JwtClaimsSet.Builder claims) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build(),
                claims.build())).getTokenValue();
    }

    public static String oro(String subject) {
        return sign(ORO, claims("oro", subject, Instant.now().plus(10, ChronoUnit.MINUTES)));
    }

    public static String umoja(String subject) {
        return sign(UMOJA, claims("umoja", subject, Instant.now().plus(10, ChronoUnit.MINUTES)));
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).keyIDFromThumbprint(true).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
