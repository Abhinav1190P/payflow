package com.payflow.common.error;

import org.springframework.http.HttpStatus;

// Concept: custom unchecked exception hierarchy. Callers are not forced to catch business errors;
// the global handler turns each subclass into the right HTTP status and error code.
public abstract class PayflowException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected PayflowException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
