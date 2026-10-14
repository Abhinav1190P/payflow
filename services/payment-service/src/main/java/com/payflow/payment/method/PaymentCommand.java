package com.payflow.payment.method;

import java.util.UUID;

public record PaymentCommand(UUID userId, long amountMinor, String merchantReference, String cardNumber) {

    @Override
    public String toString() {
        return "PaymentCommand[userId=" + userId + ", amountMinor=" + amountMinor + ", merchantReference=" + merchantReference + ", card=***]";
    }
}
