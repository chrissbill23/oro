package com.asata.org.oro.exception;

/** the resource does not exist or is not visible to the caller (the same answer: an id says nothing) */
public class NotFound extends RuntimeException {
    public NotFound(String message) {
        super(message);
    }
}
