package com.asata.org.oro.config.security;

import com.asata.org.oro.enums.Product;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * oro.security.*: whom Oro trusts. Each product authenticates its own users and signs their access tokens (RS256) with
 * its own private key, which only the product holds; Oro checks a token with the public keys of the product its issuer
 * names (published as a JWK set), so it can verify a token but never sign one, and a token of one product is never
 * accepted as the other's. The tokens name oro among their audience. The trusted integration of a product (identity
 * provisioning, Umoja organization accesses) calls /internal with its own integration key. Unset keys disable that door.
 */
@Data
@ConfigurationProperties(prefix = "oro.security")
public class SecurityProperties {

    /** origins allowed to call the API from a browser */
    private List<String> corsAllowedOrigins = List.of("http://localhost:4200", "http://localhost:4201");

    private Map<Product, ProductTrust> products = new EnumMap<>(Product.class);

    @Data
    public static class ProductTrust {
        /** the iss claim of the tokens of the product (umoja: as Umoja issues them) */
        private String issuer;
        /**
         * where the product publishes its public keys (a JWK set, e.g. Umoja's /umoja/.well-known/jwks.json), read at the
         * first token and again for an unknown kid (a rotation); unset with jwk-set unset: no user token
         */
        private String jwkSetUri;
        /** the public keys as a JWK set, inline, instead of jwk-set-uri (the tests, a static setup) */
        private String jwkSet;
        /** Oro's name among the audience of the tokens of the product */
        private String audience = "oro";
        /**
         * the keys of the trusted integration of the product (x-integration-key), as "name:sha256": a name for the logs,
         * and the SHA-256 of the key in hex, never the key itself ({@code printf %s "$KEY" | shasum -a 256}). Several
         * during a rotation; none: no integration
         */
        private List<String> integrationKeys = List.of();
    }

    public Optional<Product> productOfIssuer(String issuer) {
        return products.entrySet().stream()
                .filter(entry -> entry.getValue().getIssuer() != null && entry.getValue().getIssuer().equals(issuer))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
