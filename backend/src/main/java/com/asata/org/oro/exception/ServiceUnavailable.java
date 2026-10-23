package com.asata.org.oro.exception;

/** Oro cannot take the request now (a capacity reached): the caller may try again after retryAfterSeconds */
public class ServiceUnavailable extends RuntimeException {

    private final long retryAfterSeconds;

    public ServiceUnavailable(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
