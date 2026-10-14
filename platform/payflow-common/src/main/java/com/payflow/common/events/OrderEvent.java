package com.payflow.common.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

// Facts published by order-service when a saga ends; notification-service listens to these.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderEvent.OrderConfirmed.class, name = "OrderConfirmed"),
        @JsonSubTypes.Type(value = OrderEvent.OrderCancelled.class, name = "OrderCancelled")
})
public sealed interface OrderEvent extends SagaMessage {

    UUID userId();

    record OrderConfirmed(String messageId, UUID orderId, UUID userId, long totalMinor) implements OrderEvent {
    }

    record OrderCancelled(String messageId, UUID orderId, UUID userId, String reason) implements OrderEvent {
    }
}
