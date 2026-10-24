package com.asata.org.oro.config.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

/** what a client hears when Oro fails, without a server */
class HttpExceptionHandlerTest {

    private final HttpExceptionHandler handler = new HttpExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oro/conversations");

    /** no connection free in time (the load test saw it at saturation): 503 and when to retry, not 500 */
    @Test
    void anExhaustedPoolIsBusyNotBroken() {
        var response = handler.internal(new CannotCreateTransactionException("Could not open JPA EntityManager",
                new RuntimeException(new SQLTransientConnectionException("oro-api - Connection is not available"))), request);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
    }

    @Test
    void anythingElseIsAnInternalError() {
        assertThat(handler.internal(new IllegalStateException("a bug"), request).getStatusCode().value()).isEqualTo(500);
    }
}
