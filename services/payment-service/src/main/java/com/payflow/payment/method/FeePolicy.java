package com.payflow.payment.method;

// Concept: functional interface + default methods. One abstract method means any lambda
// "amount -> fee" is a FeePolicy. Default methods add behaviour to every implementation without
// breaking them (cappedAt wraps an existing policy in a new one).
@FunctionalInterface
public interface FeePolicy {

    FeePolicy NONE = amountMinor -> 0;

    long feeFor(long amountMinor);

    static FeePolicy basisPoints(int bps) {
        return amountMinor -> Math.multiplyExact(amountMinor, (long) bps) / 10_000;
    }

    default FeePolicy cappedAt(long maxFeeMinor) {
        return amountMinor -> Math.min(feeFor(amountMinor), maxFeeMinor);
    }
}
