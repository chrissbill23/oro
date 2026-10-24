package com.asata.org.oro.config.security;

import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.Unauthorized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two stateless doors: /internal for the trusted integrations of the products (their keys), everything else for the
 * users of the products (their bearer tokens, checked with the secret of the product their issuer names).
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain integrationSecurityFilterChain(HttpSecurity http, SecurityProperties properties,
                                                       @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        stateless(http.securityMatcher("/internal/**"), exceptionResolver)
                .addFilterBefore(new IntegrationKeyFilter(integrationKeys(properties)), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(IntegrationKeyFilter.AUTHORITY));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityProperties properties,
                                               com.asata.org.oro.account.AccountKeys accountKeys,
                                               @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        // Oro's own tokens (product ORO): unless keys are given, the ones its accounts sign, checked in process
        SecurityProperties.ProductTrust oro = properties.getProducts().get(com.asata.org.oro.enums.Product.ORO);
        if (oro != null && (oro.getJwkSetUri() == null || oro.getJwkSetUri().isBlank())
                && (oro.getJwkSet() == null || oro.getJwkSet().isBlank())) {
            oro.setJwkSet(accountKeys.publicKeys().toString(true));
        }
        Map<String, AuthenticationManager> managers = userTokenManagers(properties);
        stateless(http, exceptionResolver)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .authorizeHttpRequests(auth -> auth
                        // the end of a stream (SseEmitter) is an async dispatch of a request authenticated at its start
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/ping", "/error", "/actuator/health/**", "/doc/**").permitAll()
                        // the sign-in of Oro's accounts, and the public keys of its tokens
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/login", "/auth/refresh", "/auth/logout",
                                "/auth/signup", "/auth/verify-email", "/auth/verify-email/resend").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                        // the actuator is served on the management port only (never published): Prometheus, unauthenticated
                        .requestMatchers("/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        // the issuer, read from the token, picks the key; an unknown issuer is refused
                        .authenticationManagerResolver(new JwtIssuerAuthenticationManagerResolver(managers::get))
                        .authenticationEntryPoint((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, new Unauthorized("Authentication required"))));
        return http.build();
    }

    private static HttpSecurity stateless(HttpSecurity http, HandlerExceptionResolver exceptionResolver) throws Exception {
        return http
                // bearer tokens and keys, no cookie: no CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, new Unauthorized("Authentication required")))
                        .accessDeniedHandler((request, response, e) -> exceptionResolver.resolveException(request, response, null, e)));
    }

    /** the form of an account id in its product (the external id of oro_messaging_identity): never an email */
    static final Pattern SUBJECT = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    /** the clocks of the products and Oro may differ this much */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    /**
     * issuer -> the authentication of its tokens: RS256 only, with the public keys of its product (by the kid of the
     * token), its issuer, oro among its audience, an expiry (past by 30 s at most) and a not-before (if any) reached, a
     * subject in the form of an account id. Oro holds no key that could sign a token.
     */
    static Map<String, AuthenticationManager> userTokenManagers(SecurityProperties properties) {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        properties.getProducts().forEach((product, trust) -> {
            boolean uri = trust.getJwkSetUri() != null && !trust.getJwkSetUri().isBlank();
            boolean inline = trust.getJwkSet() != null && !trust.getJwkSet().isBlank();
            if (!uri && !inline) {
                log.warn("no public keys for {} (jwk-set-uri): its users cannot call Oro", product);
                return;
            }
            if (trust.getIssuer() == null || trust.getIssuer().isBlank() || trust.getAudience() == null || trust.getAudience().isBlank()) {
                throw new IllegalStateException("oro.security.products." + product + ": an issuer and an audience");
            }
            NimbusJwtDecoder decoder = uri
                    ? NimbusJwtDecoder.withJwkSetUri(trust.getJwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256).build()
                    : inlineDecoder(product, trust.getJwkSet());
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(CLOCK_SKEW),
                    new JwtIssuerValidator(trust.getIssuer()),
                    new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, audience -> audience != null && audience.contains(trust.getAudience())),
                    new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && SUBJECT.matcher(subject).matches())));
            managers.put(trust.getIssuer(), new JwtAuthenticationProvider(decoder)::authenticate);
        });
        return managers;
    }

    /** the public keys given inline (never a private part: only the public ones are kept), RS256 only */
    private static NimbusJwtDecoder inlineDecoder(Product product, String jwkSet) {
        JWKSet keys;
        try {
            keys = JWKSet.parse(jwkSet).toPublicJWKSet();
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("oro.security.products." + product + ".jwk-set: not a JWK set", e);
        }
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(keys)));
        // the claims are the validators' to check
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
        return new NimbusJwtDecoder(processor);
    }

    /** a key of an integration in the configuration: its name, then the SHA-256 of its value in hex */
    static final Pattern INTEGRATION_KEY = Pattern.compile("([A-Za-z0-9._-]{1,32}):([0-9a-f]{64})");

    /**
     * the keys of the integrations, from oro.security.products.*.integration-keys ("name:sha256", several during a
     * rotation). A name once per product; a key for one product only.
     */
    static List<IntegrationKeyFilter.IntegrationKey> integrationKeys(SecurityProperties properties) {
        List<IntegrationKeyFilter.IntegrationKey> keys = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        properties.getProducts().forEach((product, trust) -> {
            Set<String> names = new HashSet<>();
            for (String entry : trust.getIntegrationKeys() == null ? List.<String>of() : trust.getIntegrationKeys()) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                Matcher key = INTEGRATION_KEY.matcher(entry.strip().toLowerCase(java.util.Locale.ROOT));
                String where = "oro.security.products." + product + ".integration-keys";
                if (!key.matches()) {
                    throw new IllegalStateException(where + ": name:sha256 (the SHA-256 of the key in hex), never the key itself");
                }
                if (!names.add(key.group(1)) || !hashes.add(key.group(2))) {
                    throw new IllegalStateException(where + ": a name once, a key for one product only");
                }
                keys.add(new IntegrationKeyFilter.IntegrationKey(product, key.group(1), HexFormat.of().parseHex(key.group(2))));
            }
        });
        return keys;
    }

    static CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getCorsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("x-request-id", "x-organization-id", "Content-Type", "Authorization"));
        cors.setExposedHeaders(List.of("x-request-id"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
