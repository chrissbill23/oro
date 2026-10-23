package com.asata.org.oro.config.production;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** production does not start on the settings of development, and says all that is wrong, never a secret */
class ProductionChecksTest {

    private static MockEnvironment production() {
        return new MockEnvironment()
                .withProperty("spring.datasource.password", "a-long-password-of-the-vault-01")
                .withProperty("oro.system-datasource.password", "another-long-password-of-the-vault")
                .withProperty("oro.attachments.scan", "clamav")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("oro.security.cors-allowed-origins", "https://umoja.asata.example")
                .withProperty("server.port", "8082")
                .withProperty("management.server.port", "9082")
                .withProperty("oro.security.products.UMOJA.jwk-set-uri", "http://umoja:8080/umoja/.well-known/jwks.json")
                .withProperty("oro.security.products.UMOJA.integration-keys", "current:" + "a".repeat(64));
    }

    @Test
    void aProductionSetupStarts() {
        assertThat(ProductionChecks.problems(production())).isEmpty();
        new ProductionChecks(production());
    }

    @Test
    void theSettingsOfDevelopmentAreRefusedAllAtOnce() {
        MockEnvironment development = production()
                .withProperty("spring.datasource.password", "oro_api_dev")
                .withProperty("oro.system-datasource.password", "")
                .withProperty("oro.attachments.scan", "none")
                .withProperty("springdoc.api-docs.enabled", "true")
                .withProperty("oro.rate-limits.enabled", "false")
                .withProperty("oro.security.cors-allowed-origins", "http://localhost:4201,https://umoja.asata.example")
                .withProperty("management.server.port", "8082")
                .withProperty("oro.security.products.UMOJA.integration-keys", "");

        assertThat(ProductionChecks.problems(development)).hasSize(8);
        assertThatThrownBy(() -> new ProductionChecks(development))
                .hasMessageContaining("does not start in production")
                .hasMessageContaining("scan is not clamav")
                .hasMessageContaining("http://localhost:4201")
                // the name of a setting, never its value
                .hasMessageNotContaining("oro_api_dev");
    }
}
