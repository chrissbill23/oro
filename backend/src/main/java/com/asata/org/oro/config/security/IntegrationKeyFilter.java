package com.asata.org.oro.config.security;

import com.asata.org.oro.enums.Product;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Authenticates the trusted integration of a product by its key (header x-integration-key, compared in constant
 * time). The principal is the product: an integration acts for its own product only.
 */
public class IntegrationKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "x-integration-key";
    public static final String AUTHORITY = "integration";

    private final Map<Product, byte[]> keys;

    public IntegrationKeyFilter(Map<Product, byte[]> keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null) {
            byte[] candidate = presented.getBytes(StandardCharsets.UTF_8);
            for (Map.Entry<Product, byte[]> key : keys.entrySet()) {
                if (MessageDigest.isEqual(key.getValue(), candidate)) {
                    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            key.getKey(), null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
                    break;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
