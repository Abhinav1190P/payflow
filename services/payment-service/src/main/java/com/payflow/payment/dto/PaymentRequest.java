package com.payflow.payment.dto;

import com.payflow.payment.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
        @NotNull PaymentMethodType method,
        @Positive long amountMinor,
        @NotBlank @Size(max = 100) String merchantReference,
        @Pattern(regexp = "\\d{12,19}") String cardNumber) {

    public String cardLast4() {
        return cardNumber == null || cardNumber.length() < 4 ? null : cardNumber.substring(cardNumber.length() - 4);
    }

    @Override
    public String toString() {
        return "PaymentRequest[method=" + method + ", amountMinor=" + amountMinor + ", merchantReference=" + merchantReference + "]";
    }
}
