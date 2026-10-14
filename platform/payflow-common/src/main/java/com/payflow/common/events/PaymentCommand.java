package com.payflow.common.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

// Concept: sealed interface + Jackson polymorphism. The JSON carries a "type" field; Jackson picks the record.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaymentCommand.ChargePayment.class, name = "ChargePayment"),
        @JsonSubTypes.Type(value = PaymentCommand.RefundPayment.class, name = "RefundPayment")
})
public sealed interface PaymentCommand extends SagaMessage {

    record ChargePayment(String messageId, UUID orderId, UUID userId, long amountMinor) implements PaymentCommand {
    }

    record RefundPayment(String messageId, UUID orderId, UUID userId, long amountMinor) implements PaymentCommand {
    }
}
