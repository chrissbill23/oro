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

    /** a client that gave up before its answer: nothing written, and not an ERROR (the alert of the errors) */
    @Test
    void aClientGoneIsNotAnError() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(HttpExceptionHandler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs = new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertThat(handler.internal(new org.springframework.http.converter.HttpMessageNotWritableException(
                    "Could not write JSON", new java.io.IOException("Broken pipe")), request)).isNull();
            assertThat(handler.internal(new org.apache.catalina.connector.ClientAbortException(
                    new java.io.IOException("Connection reset by peer")), request)).isNull();
            assertThat(logs.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN));
            // an IOException of Oro's own (a file, the disk) is still an error
            assertThat(handler.internal(new java.io.UncheckedIOException(new java.io.IOException("No space left on device")), request)
                    .getStatusCode().value()).isEqualTo(500);
            assertThat(logs.list).anyMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR);
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void anythingElseIsAnInternalError() {
        assertThat(handler.internal(new IllegalStateException("a bug"), request).getStatusCode().value()).isEqualTo(500);
    }
}
