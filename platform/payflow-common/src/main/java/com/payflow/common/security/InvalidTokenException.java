package com.payflow.common.security;

// Concept: checked exception. A bad token is an expected, recoverable situation the caller
// must decide how to handle, so the compiler forces the JWT filter to catch it.
public class InvalidTokenException extends Exception {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
