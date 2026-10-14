package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public class PaymentGatewayException extends PayflowException {

    public PaymentGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", message);
    }
}
