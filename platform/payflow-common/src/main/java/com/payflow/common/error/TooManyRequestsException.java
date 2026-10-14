package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends PayflowException {

    public TooManyRequestsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many attempts, try again in a minute");
    }
}
