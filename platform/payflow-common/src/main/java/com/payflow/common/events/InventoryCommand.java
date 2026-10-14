package com.payflow.common.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InventoryCommand.ReserveStock.class, name = "ReserveStock")
})
public sealed interface InventoryCommand extends SagaMessage {

    record Line(UUID productId, int quantity) {
    }

    record ReserveStock(String messageId, UUID orderId, List<Line> lines) implements InventoryCommand {
    }
}
