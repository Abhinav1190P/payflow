package com.payflow.payment.method;

import java.util.UUID;

// Concept: sealed interface + records. A payment ends in exactly one of three shapes; the switch in
// PaymentService handles all three and the compiler checks nothing is missed.
public sealed interface PaymentResult {

    record Approved(String reference, UUID transactionId, long amountMinor, long feeMinor, Long balanceAfterMinor)
            implements PaymentResult {
    }

    record Declined(String code, String reason) implements PaymentResult {
    }

    record Failed(String reason) implements PaymentResult {
    }
}
