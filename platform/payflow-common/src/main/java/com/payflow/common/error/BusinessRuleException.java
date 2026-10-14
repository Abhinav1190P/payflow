package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends PayflowException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
