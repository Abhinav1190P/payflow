package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends PayflowException {

    public InsufficientFundsException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Wallet balance is too low for this amount");
    }
}
