package com.asata.org.oro.exception;

/** the request would put the caller over a size it is allowed (its storage quota) */
public class PayloadTooLarge extends RuntimeException {
    public PayloadTooLarge(String message) {
        super(message);
    }
}
