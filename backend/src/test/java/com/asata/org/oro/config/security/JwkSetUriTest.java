package com.asata.org.oro.config.security;

import com.asata.org.oro.TestTokens;
import com.asata.org.oro.enums.Product;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Oro reads the public keys of a product from where it publishes them (Umoja: /umoja/.well-known/jwks.json): at the
 * first token, then again for a kid it does not know, so a rotation needs no restart of Oro
 */
class JwkSetUriTest {

    private HttpServer server;
    private final AtomicReference<String> published = new AtomicReference<>();
    private final AtomicInteger fetches = new AtomicInteger();
    private AuthenticationManager umoja;

    @BeforeEach
    void publish() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/umoja/.well-known/jwks.json", exchange -> {
            fetches.incrementAndGet();
            byte[] body = published.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/jwk-set+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        SecurityProperties properties = new SecurityProperties();
        SecurityProperties.ProductTrust trust = new SecurityProperties.ProductTrust();
        trust.setIssuer("umoja");
        trust.setJwkSetUri("http://127.0.0.1:" + server.getAddress().getPort() + "/umoja/.well-known/jwks.json");
        properties.getProducts().put(Product.UMOJA, trust);
        umoja = SecurityConfig.userTokenManagers(properties).get("umoja");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private boolean accepted(RSAKey key) {
        String token = TestTokens.sign(key, TestTokens.claims("umoja", "umoja-jordan", Instant.now().plusSeconds(600)));
        try {
            return umoja.authenticate(new BearerTokenAuthenticationToken(token)).isAuthenticated();
        } catch (AuthenticationException e) {
            return false;
        }
    }

    @Test
    void aRotationIsReadWithoutARestart() throws Exception {
        RSAKey first = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        RSAKey next = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        published.set(TestTokens.publicJwkSet(first));
        assertThat(fetches.get()).as("nothing read before the first token").isZero();

        assertThat(accepted(first)).isTrue();
        assertThat(accepted(first)).isTrue();
        assertThat(fetches.get()).as("read once, then cached").isEqualTo(1);

        // Umoja publishes its next key, then signs with it: Oro reads the set again for that kid
        published.set(TestTokens.publicJwkSet(next, first));
        assertThat(accepted(next)).isTrue();
        assertThat(accepted(first)).isTrue();
        assertThat(fetches.get()).isEqualTo(2);
    }

    @Test
    void aKeyThatIsNotPublishedIsRefused() throws Exception {
        published.set(TestTokens.publicJwkSet(TestTokens.UMOJA));
        assertThat(accepted(TestTokens.UMOJA)).isTrue();
        assertThat(accepted(TestTokens.OTHER)).isFalse();
        // and the private part of a published key is never used: only the public one is in the set
        assertThat(published.get()).doesNotContain("\"d\":");
    }

    @Test
    void withoutKeysTheProductHasNoDoor() {
        SecurityProperties properties = new SecurityProperties();
        SecurityProperties.ProductTrust trust = new SecurityProperties.ProductTrust();
        trust.setIssuer("oro");
        properties.getProducts().put(Product.ORO, trust);
        assertThat(SecurityConfig.userTokenManagers(properties)).isEmpty();
        assertThatThrownBy(() -> {
            trust.setJwkSet("{\"keys\": 3}");
            SecurityConfig.userTokenManagers(properties);
        }).isInstanceOf(IllegalStateException.class);
    }
}
