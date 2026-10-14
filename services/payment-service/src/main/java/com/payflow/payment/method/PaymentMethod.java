package com.payflow.payment.method;

import com.payflow.payment.PaymentMethodType;
import java.util.Objects;
import java.util.Optional;

// Concept: abstract class + inheritance. Shared flow lives here; WalletPayment and CardPaymentStub
// only supply the parts that differ.
// Concept: Template Method. pay() is final and fixes the order of steps (validate -> fee -> charge);
// subclasses override the steps, never the order.
// Concept: composition over inheritance. The fee rule is a FeePolicy object passed in, not a subclass
// per fee type, so any method can use any fee rule.
public abstract class PaymentMethod {

    private final FeePolicy feePolicy;

    protected PaymentMethod(FeePolicy feePolicy) {
        this.feePolicy = Objects.requireNonNull(feePolicy);
    }

    public abstract PaymentMethodType type();

    public final PaymentResult pay(PaymentCommand command) {
        Optional<String> problem = validate(command);
        if (problem.isPresent()) {
            return new PaymentResult.Declined("INVALID_PAYMENT", problem.get());
        }
        long fee = feePolicy.feeFor(command.amountMinor());
        return charge(command, fee);
    }

    protected Optional<String> validate(PaymentCommand command) {
        if (command.amountMinor() <= 0) {
            return Optional.of("Amount must be positive");
        }
        return Optional.empty();
    }

    protected abstract PaymentResult charge(PaymentCommand command, long feeMinor);
}
