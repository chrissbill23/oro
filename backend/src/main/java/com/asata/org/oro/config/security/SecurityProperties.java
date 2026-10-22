package com.asata.org.oro.config.security;

import com.asata.org.oro.enums.Product;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * oro.security.*: whom Oro trusts. Each product authenticates its own users and signs their access tokens (HS256)
 * with its own secret and issuer; Oro checks a token with the secret of the product its issuer names, so a token of
 * one product is never accepted as the other's. The trusted integration of a product (identity provisioning, Umoja
 * organization accesses) calls /internal with its own integration key. An unset secret or key disables that door.
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
        /** the HMAC-SHA256 key the product signs its access tokens with, at least 32 bytes; unset: no user token */
        private String jwtSecret;
        /** the key of the trusted integration of the product (x-integration-key), at least 32 characters; unset: none */
        private String integrationKey;
    }

    public Optional<Product> productOfIssuer(String issuer) {
        return products.entrySet().stream()
                .filter(entry -> entry.getValue().getIssuer() != null && entry.getValue().getIssuer().equals(issuer))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
