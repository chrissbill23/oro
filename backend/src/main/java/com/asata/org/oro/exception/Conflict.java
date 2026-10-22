package com.asata.org.oro.exception;

/** the request conflicts with the current state */
public class Conflict extends RuntimeException {
    public Conflict(String message) {
        super(message);
    }
}
