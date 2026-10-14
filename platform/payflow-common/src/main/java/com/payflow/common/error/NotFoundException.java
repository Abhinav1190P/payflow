package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends PayflowException {

    public NotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }
}
