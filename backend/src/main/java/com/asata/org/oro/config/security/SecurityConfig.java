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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
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
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                                               @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        Map<String, AuthenticationManager> managers = userTokenManagers(properties);
        stateless(http, exceptionResolver)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .authorizeHttpRequests(auth -> auth
                        // the end of a stream (SseEmitter) is an async dispatch of a request authenticated at its start
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/ping", "/error", "/actuator/health/**", "/doc/**").permitAll()
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

    /** issuer -> the authentication of its tokens: HS256 with the secret of its product, its issuer, not expired */
    static Map<String, AuthenticationManager> userTokenManagers(SecurityProperties properties) {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        properties.getProducts().forEach((product, trust) -> {
            if (trust.getJwtSecret() == null || trust.getJwtSecret().isBlank()) {
                log.warn("no JWT secret for {}: its users cannot call Oro", product);
                return;
            }
            byte[] secret = trust.getJwtSecret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32 || trust.getIssuer() == null || trust.getIssuer().isBlank()) {
                throw new IllegalStateException("oro.security.products." + product + ": an issuer and a jwt-secret of 32 bytes at least");
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(trust.getIssuer()));
            managers.put(trust.getIssuer(), new JwtAuthenticationProvider(decoder)::authenticate);
        });
        return managers;
    }

    static Map<Product, byte[]> integrationKeys(SecurityProperties properties) {
        Map<Product, byte[]> keys = new EnumMap<>(Product.class);
        properties.getProducts().forEach((product, trust) -> {
            if (trust.getIntegrationKey() == null || trust.getIntegrationKey().isBlank()) {
                return;
            }
            if (trust.getIntegrationKey().length() < 32) {
                throw new IllegalStateException("oro.security.products." + product + ".integration-key: 32 characters at least");
            }
            keys.put(product, trust.getIntegrationKey().getBytes(StandardCharsets.UTF_8));
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
