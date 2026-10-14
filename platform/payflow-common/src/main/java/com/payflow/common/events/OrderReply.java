package com.payflow.common.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

// Replies from the saga participants (payment-service, inventory-service) back to the orchestrator (order-service).
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderReply.PaymentCompleted.class, name = "PaymentCompleted"),
        @JsonSubTypes.Type(value = OrderReply.PaymentFailed.class, name = "PaymentFailed"),
        @JsonSubTypes.Type(value = OrderReply.PaymentRefunded.class, name = "PaymentRefunded"),
        @JsonSubTypes.Type(value = OrderReply.StockReserved.class, name = "StockReserved"),
        @JsonSubTypes.Type(value = OrderReply.StockRejected.class, name = "StockRejected")
})
public sealed interface OrderReply extends SagaMessage {

    record PaymentCompleted(String messageId, UUID orderId, UUID transactionId) implements OrderReply {
    }

    record PaymentFailed(String messageId, UUID orderId, String reason) implements OrderReply {
    }

    record PaymentRefunded(String messageId, UUID orderId) implements OrderReply {
    }

    record StockReserved(String messageId, UUID orderId) implements OrderReply {
    }

    record StockRejected(String messageId, UUID orderId, String reason) implements OrderReply {
    }
}
