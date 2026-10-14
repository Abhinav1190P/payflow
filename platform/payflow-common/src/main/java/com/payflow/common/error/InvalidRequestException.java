package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends PayflowException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
