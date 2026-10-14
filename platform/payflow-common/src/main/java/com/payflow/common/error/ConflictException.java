package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends PayflowException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
