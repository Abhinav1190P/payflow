package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class PreconditionFailedException extends PayflowException {

    public PreconditionFailedException(String message) {
        super(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", message);
    }
}
