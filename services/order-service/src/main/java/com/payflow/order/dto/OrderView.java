package com.payflow.order.dto;

import com.payflow.order.Order;
import com.payflow.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID id, OrderStatus status, long totalMinor, String cancelReason, Instant createdAt,
                        Instant updatedAt, List<Item> items) {

    public record Item(UUID productId, String productName, long unitPriceMinor, int quantity, long lineTotalMinor) {
    }

    public static OrderView from(Order order) {
        return new OrderView(order.getId(), order.getStatus(), order.getTotalMinor(), order.getCancelReason(),
                order.getCreatedAt(), order.getUpdatedAt(),
                order.getItems().stream()
                        .map(i -> new Item(i.getProductId(), i.getProductName(), i.getUnitPriceMinor(), i.getQuantity(), i.lineTotalMinor()))
                        .toList());
    }

    public static OrderView summary(Order order) {
        return new OrderView(order.getId(), order.getStatus(), order.getTotalMinor(), order.getCancelReason(),
                order.getCreatedAt(), order.getUpdatedAt(), List.of());
    }
}
