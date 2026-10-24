package com.asata.org.oro.config.production;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The production profile refuses to start on a setting of development: every problem at once, never a secret in the
 * message. What a production needs:
 * <ul>
 * <li>passwords of its own for the three logins (never a placeholder of the development or the tests);</li>
 * <li>the signing key of its accounts (a key made at each start would sign everyone out at each restart), their
 * refresh cookie Secure;</li>
 * <li>the malware scan by ClamAV, no API description, the rate limits on;</li>
 * <li>browser origins over HTTPS only, none on localhost;</li>
 * <li>the actuator on a port of its own (never the API's: it would be public);</li>
 * <li>the keys of the Umoja integration when Umoja's tokens are accepted.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("production")
public class ProductionChecks {

    /** the passwords of the development and the tests: public, in the repositories */
    static final Set<String> PLACEHOLDERS = Set.of("oro_api_dev", "oro_worker_dev", "oro_api_test", "oro_worker_test",
            "oro_auth_dev", "oro_auth_test", "oro_service_dev", "oro_service_test", "postgres", "password", "changeme");

    public ProductionChecks(Environment environment) {
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Oro does not start in production with a setting of development:\n - "
                    + String.join("\n - ", problems));
        }
        log.info("production settings checked");
    }

    static List<String> problems(Environment env) {
        List<String> problems = new ArrayList<>();
        password(env, "spring.datasource.password", "the API's database login", problems);
        password(env, "oro.system-datasource.password", "the worker's database login", problems);
        password(env, "oro.account-datasource.password", "the accounts' database login", problems);
        if (env.getProperty("oro.accounts.signing-key", "").isBlank()) {
            problems.add("no signing key for the accounts (ORO_ACCOUNTS_SIGNING_KEY): every restart would sign everyone out");
        }
        if (!env.getProperty("oro.accounts.cookie-secure", Boolean.class, true)) {
            problems.add("the refresh cookie is not Secure (ORO_COOKIE_SECURE)");
        }
        String smtp = env.getProperty("spring.mail.host", "");
        if (smtp.isBlank() || smtp.equals("localhost") || smtp.equals("mailpit") || smtp.startsWith("127.")) {
            problems.add("the emails of the accounts go to a development mailbox (ORO_SMTP_HOST)");
        }
        if (!env.getProperty("oro.accounts.link-base", "").startsWith("https://")) {
            problems.add("the links of the emails are not HTTPS (ORO_ACCOUNTS_LINK_BASE)");
        }
        if (!"clamav".equals(env.getProperty("oro.attachments.scan"))) {
            problems.add("oro.attachments.scan is not clamav: files would be served unscanned");
        }
        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, true) || env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
            problems.add("the API description is on (ORO_API_DOCS)");
        }
        if (!env.getProperty("oro.rate-limits.enabled", Boolean.class, true)) {
            problems.add("the rate limits are off (ORO_RATE_LIMITS)");
        }
        String origins = env.getProperty("oro.security.cors-allowed-origins", "");
        for (String origin : origins.split(",")) {
            String o = origin.strip();
            if (!o.isEmpty() && (!o.startsWith("https://") || o.contains("localhost") || o.contains("127.0.0.1"))) {
                problems.add("a browser origin is not HTTPS or is local: " + o);
            }
        }
        String management = env.getProperty("management.server.port", "");
        if (management.isBlank() || management.equals(env.getProperty("server.port", "8080"))) {
            problems.add("the actuator is on the port of the API (management.server.port): the metrics would be public");
        }
        boolean umojaTokens = !env.getProperty("oro.security.products.UMOJA.jwk-set-uri", "").isBlank()
                || !env.getProperty("oro.security.products.UMOJA.jwk-set", "").isBlank();
        if (umojaTokens && env.getProperty("oro.security.products.UMOJA.integration-keys", "").isBlank()) {
            problems.add("Umoja's tokens are accepted but its integration has no key (UMOJA_INTEGRATION_KEYS)");
        }
        return problems;
    }

    private static void password(Environment env, String property, String what, List<String> problems) {
        String value = env.getProperty(property, "");
        if (value.isBlank()) {
            problems.add("no password for " + what + " (" + property + ")");
        } else if (PLACEHOLDERS.contains(value) || value.length() < 16) {
            problems.add("the password of " + what + " is a placeholder or shorter than 16 characters (" + property + ")");
        }
    }
}
