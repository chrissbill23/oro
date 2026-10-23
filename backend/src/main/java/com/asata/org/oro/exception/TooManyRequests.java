package com.asata.org.oro.exception;

/** the caller went over a rate limit: it may try again after retryAfterSeconds */
public class TooManyRequests extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequests(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
