package com.asata.org.oro.exception;

/** the request is well formed, but what it carries is refused (a file found infected) */
public class Unprocessable extends RuntimeException {
    public Unprocessable(String message) {
        super(message);
    }
}
