package com.asata.org.oro.account;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The keys of Oro's access tokens: the private key that signs them (oro.accounts.signing-key, never published nor
 * logged), and the public keys of the current and the former ones, published (/.well-known/jwks.json) and used by Oro
 * itself to check its tokens (product ORO). Without a key, a new one at each start: development only, every token
 * dies with a restart (ProductionChecks refuses it).
 */
@Slf4j
@Component
public class AccountKeys {

    private static final int GENERATED_KEY_BITS = 3072;

    private final RSAKey signingKey;
    private final JWKSet publicKeys;
    private final boolean generated;

    public AccountKeys(AccountProperties properties) {
        String configured = properties.getSigningKey();
        this.generated = configured == null || configured.isBlank();
        this.signingKey = generated ? generate() : parse(configured);
        if (generated) {
            log.warn("oro.accounts.signing-key (ORO_ACCOUNTS_SIGNING_KEY) is not set: a new signing key for this run, "
                    + "the tokens it signs die with it (development only)");
        }
        List<JWK> keys = new ArrayList<>(List.of(signingKey.toPublicJWK()));
        properties.getPreviousKeys().stream().filter(key -> key != null && !key.isBlank())
                .forEach(key -> keys.add(parse(key).toPublicJWK()));
        this.publicKeys = new JWKSet(keys);
    }

    /** the private key: only the issuer of the tokens touches it */
    RSAKey signingKey() {
        return signingKey;
    }

    /** the public keys, as published: never a private part */
    public JWKSet publicKeys() {
        return publicKeys;
    }

    /** no key was configured: one of this run only */
    public boolean generated() {
        return generated;
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(GENERATED_KEY_BITS).keyUse(KeyUse.SIGNATURE).keyIDFromThumbprint(true).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("cannot generate a signing key", e);
        }
    }

    /** a PKCS#8 RSA private key, base64 on one line (spaces and line breaks ignored) */
    static RSAKey parse(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (!(factory.generatePrivate(new PKCS8EncodedKeySpec(der)) instanceof RSAPrivateCrtKey key)) {
                throw new IllegalStateException("oro.accounts.signing-key: not an RSA private key with its public part");
            }
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
            if (publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("oro.accounts.signing-key: 2048 bits at least");
            }
            return new RSAKey.Builder(publicKey).privateKey(key).keyUse(KeyUse.SIGNATURE).keyIDFromThumbprint().build();
        } catch (GeneralSecurityException | IllegalArgumentException | JOSEException e) {
            // never the key itself in the message
            throw new IllegalStateException("oro.accounts.signing-key: not a PKCS#8 RSA private key in base64", e);
        }
    }
}
