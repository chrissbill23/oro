package com.asata.org.oro.config.security;

import com.asata.org.oro.enums.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** the configuration of the keys: name:sha256 only, a name once per product, a key for one product only */
class IntegrationKeysConfigTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private static SecurityProperties keys(Product product, String... entries) {
        SecurityProperties properties = new SecurityProperties();
        SecurityProperties.ProductTrust trust = new SecurityProperties.ProductTrust();
        trust.setIntegrationKeys(List.of(entries));
        properties.getProducts().put(product, trust);
        return properties;
    }

    @Test
    void namedHashesAreRead() {
        assertThat(SecurityConfig.integrationKeys(keys(Product.UMOJA, "current:" + HASH_A, " next:" + HASH_B.toUpperCase(), "")))
                .extracting(IntegrationKeyFilter.IntegrationKey::product, IntegrationKeyFilter.IntegrationKey::id)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Product.UMOJA, "current"),
                        org.assertj.core.groups.Tuple.tuple(Product.UMOJA, "next"));
        assertThat(SecurityConfig.integrationKeys(keys(Product.UMOJA))).isEmpty();
    }

    @Test
    void aKeyItselfOrAnAmbiguousListIsRefused() {
        // the key itself, not its hash
        assertThatThrownBy(() -> SecurityConfig.integrationKeys(keys(Product.UMOJA, "test-umoja-integration-key-for-the-tests-0123456789")))
                .hasMessageContaining("never the key itself");
        assertThatThrownBy(() -> SecurityConfig.integrationKeys(keys(Product.UMOJA, "current:" + HASH_A, "current:" + HASH_B)))
                .hasMessageContaining("a name once");
        // the same key for two products
        SecurityProperties both = keys(Product.UMOJA, "current:" + HASH_A);
        SecurityProperties.ProductTrust oro = new SecurityProperties.ProductTrust();
        oro.setIntegrationKeys(List.of("current:" + HASH_A));
        both.getProducts().put(Product.ORO, oro);
        assertThatThrownBy(() -> SecurityConfig.integrationKeys(both)).hasMessageContaining("a key for one product only");
    }
}
