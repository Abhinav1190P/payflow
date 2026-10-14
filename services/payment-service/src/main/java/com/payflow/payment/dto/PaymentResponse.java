package com.payflow.payment.dto;

import com.payflow.payment.PaymentMethodType;
import com.payflow.payment.method.PaymentResult;
import java.util.UUID;

public record PaymentResponse(String status, PaymentMethodType method, String reference, UUID transactionId,
                              long amountMinor, long feeMinor, Long balanceAfterMinor, String declineCode, String message) {

    public static PaymentResponse approved(PaymentMethodType method, PaymentResult.Approved a) {
        return new PaymentResponse("APPROVED", method, a.reference(), a.transactionId(), a.amountMinor(), a.feeMinor(),
                a.balanceAfterMinor(), null, null);
    }

    public static PaymentResponse declined(PaymentMethodType method, long amountMinor, PaymentResult.Declined d) {
        return new PaymentResponse("DECLINED", method, null, null, amountMinor, 0, null, d.code(), d.reason());
    }
}
