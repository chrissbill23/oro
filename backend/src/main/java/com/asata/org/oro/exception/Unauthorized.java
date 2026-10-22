package com.asata.org.oro.exception;

/** the request is not authenticated (no token, or not one Oro trusts) */
public class Unauthorized extends RuntimeException {
    public Unauthorized(String message) {
        super(message);
    }
}
