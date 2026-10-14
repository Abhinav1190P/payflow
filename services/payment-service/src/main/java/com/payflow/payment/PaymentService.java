package com.payflow.payment;

import com.payflow.common.error.PaymentGatewayException;
import com.payflow.idempotency.Fingerprint;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.idempotency.Replayable;
import com.payflow.payment.dto.PaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.method.PaymentCommand;
import com.payflow.payment.method.PaymentMethodFactory;
import com.payflow.payment.method.PaymentResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentMethodFactory methods;
    private final IdempotencyService idempotency;

    public PaymentService(PaymentMethodFactory methods, IdempotencyService idempotency) {
        this.methods = methods;
        this.idempotency = idempotency;
    }

    // Approved and declined results are stored for replay. A gateway failure throws instead, which rolls the
    // transaction back and stores nothing, so the client may safely retry with the same Idempotency-Key.
    @Transactional
    public Replayable<PaymentResponse> pay(UUID userId, String idempotencyKey, PaymentRequest request) {
        String fingerprint = Fingerprint.of("payment", request.method(), request.amountMinor(), request.merchantReference(), request.cardLast4());
        return idempotency.run(userId, idempotencyKey, fingerprint, PaymentResponse.class, () -> {
            var command = new PaymentCommand(userId, request.amountMinor(), request.merchantReference(), request.cardNumber());
            PaymentResult result = methods.forType(request.method()).pay(command);
            return switch (result) {
                case PaymentResult.Approved approved -> Replayable.fresh(201, PaymentResponse.approved(request.method(), approved));
                case PaymentResult.Declined declined -> Replayable.fresh(402, PaymentResponse.declined(request.method(), request.amountMinor(), declined));
                case PaymentResult.Failed failed -> throw new PaymentGatewayException(failed.reason());
            };
        });
    }
}
