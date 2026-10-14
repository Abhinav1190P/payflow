package com.payflow.order;

import com.payflow.common.events.PaymentCommand;
import com.payflow.common.events.Topics;
import com.payflow.messaging.outbox.OutboxWriter;
import com.payflow.order.client.CatalogClient;
import com.payflow.order.dto.OrderView;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderWriter {

    private final OrderRepository orders;
    private final OutboxWriter outbox;
    private final OrderMetrics metrics;
    private final Clock clock;

    public OrderWriter(OrderRepository orders, OutboxWriter outbox, OrderMetrics metrics, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    // The order row and the first saga command (charge the wallet) commit together, or not at all.
    @Transactional
    public OrderView create(UUID userId, String idempotencyKey, Map<UUID, Integer> quantities, Map<UUID, CatalogClient.ProductView> products) {
        var order = new Order(userId, idempotencyKey, clock.instant());
        quantities.forEach((productId, quantity) -> {
            var product = products.get(productId);
            order.addItem(productId, product.name(), product.priceMinor(), quantity);
        });
        Order saved = orders.save(order);
        outbox.enqueue(Topics.PAYMENT_COMMANDS, saved.getId().toString(),
                new PaymentCommand.ChargePayment(UUID.randomUUID().toString(), saved.getId(), userId, saved.getTotalMinor()));
        metrics.reached(OrderStatus.PENDING);
        return OrderView.from(saved);
    }
}
