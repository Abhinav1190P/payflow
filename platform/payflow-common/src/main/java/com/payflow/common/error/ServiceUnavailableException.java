package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends PayflowException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }
}
