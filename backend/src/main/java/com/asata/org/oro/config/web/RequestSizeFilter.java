package com.asata.org.oro.config.web;

import com.asata.org.oro.exception.PayloadTooLarge;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A request that is not an upload carries oro.max-request-bytes (1 MiB) at most, whatever the proxy in front: refused
 * at once when its Content-Length says more (413), and cut when its body, sent without a length, goes beyond. The
 * uploads (multipart) have their own limits (spring.servlet.multipart, oro.attachments.max-size-bytes).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeFilter(@Value("${oro.max-request-bytes:1048576}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        if (type != null && type.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(("{\"status\":413,\"message\":\"The request carries more than " + maxBytes
                    + " bytes\"}").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(new Bounded(request, maxBytes), response);
    }

    /** the body read up to the limit: beyond, PayloadTooLarge (413 by HttpExceptionHandler) */
    private static final class Bounded extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream bounded;

        Bounded(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (bounded == null) {
                ServletInputStream in = super.getInputStream();
                bounded = new ServletInputStream() {
                    private long read;

                    @Override
                    public int read() throws IOException {
                        int b = in.read();
                        if (b >= 0) {
                            count(1);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int n = in.read(buffer, offset, length);
                        if (n > 0) {
                            count(n);
                        }
                        return n;
                    }

                    private void count(int n) {
                        read += n;
                        if (read > maxBytes) {
                            throw new PayloadTooLarge("The request carries more than " + maxBytes + " bytes");
                        }
                    }

                    @Override
                    public boolean isFinished() {
                        return in.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return in.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        in.setReadListener(listener);
                    }
                };
            }
            return bounded;
        }
    }
}
