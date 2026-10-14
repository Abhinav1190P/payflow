package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends PayflowException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
