package com.asata.org.oro.exception;

/** the caller sees the resource but may not do this */
public class Forbidden extends RuntimeException {
    public Forbidden(String message) {
        super(message);
    }
}
