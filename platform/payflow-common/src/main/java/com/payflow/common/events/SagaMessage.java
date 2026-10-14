package com.payflow.common.events;

import java.util.UUID;

// Every saga message carries a unique id (for idempotent consumers) and the order it belongs to.
public interface SagaMessage {

    String messageId();

    UUID orderId();
}
