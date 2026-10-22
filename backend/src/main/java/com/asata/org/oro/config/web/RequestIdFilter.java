package com.asata.org.oro.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** x-request-id: the caller's (if it looks like an id: no log injection) or a new one; on the response and the logs */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "x-request-id";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !VALID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, requestId);
        request.setAttribute(HEADER, requestId);
        MDC.put(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(HEADER);
        }
    }
}
