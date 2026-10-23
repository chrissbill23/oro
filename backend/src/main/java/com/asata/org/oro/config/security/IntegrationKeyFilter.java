package com.asata.org.oro.config.security;

import com.asata.org.oro.enums.Product;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Authenticates the trusted integration of a product by its key (header x-integration-key). Oro keeps only the SHA-256
 * of each key, named (oro.security.products.*.integration-keys): the key presented is hashed and compared in constant
 * time with every one. A product may have several keys at once, the time of a rotation. The principal is the product:
 * an integration acts for its own product only. The name of the key used goes to the logs (never the key), and a refused
 * key is logged without it.
 */
@Slf4j
public class IntegrationKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "x-integration-key";
    public static final String AUTHORITY = "integration";
    /** the name of the key that authenticated the request: a request attribute and a key of the logs (MDC) */
    public static final String KEY_ID = "integration-key-id";
    /** a key is at least this long: anything shorter is not one of Oro's */
    static final int MIN_LENGTH = 32;

    /** a key of a product: its name and the SHA-256 of its value */
    public record IntegrationKey(Product product, String id, byte[] sha256) {
    }

    private final List<IntegrationKey> keys;

    public IntegrationKeyFilter(List<IntegrationKey> keys) {
        this.keys = List.copyOf(keys);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null) {
            chain.doFilter(request, response);
            return;
        }
        IntegrationKey matched = presented.length() < MIN_LENGTH ? null : match(sha256(presented));
        if (matched == null) {
            log.warn("integration key refused on {} {}", request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                matched.product(), null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        request.setAttribute(KEY_ID, matched.id());
        MDC.put(KEY_ID, matched.product().code() + "/" + matched.id());
        log.info("integration {} authenticated with its key {} on {} {}", matched.product(), matched.id(),
                request.getMethod(), request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(KEY_ID);
        }
    }

    /** every key is compared, whatever matched first: the time says nothing of which one did */
    private IntegrationKey match(byte[] hash) {
        IntegrationKey matched = null;
        for (IntegrationKey key : keys) {
            if (MessageDigest.isEqual(key.sha256(), hash) && matched == null) {
                matched = key;
            }
        }
        return matched;
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
