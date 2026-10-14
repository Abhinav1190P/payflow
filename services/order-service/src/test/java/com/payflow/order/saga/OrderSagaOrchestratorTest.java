package com.payflow.order.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.common.events.OrderReply;
import com.payflow.messaging.outbox.OutboxMessage;
import com.payflow.messaging.outbox.OutboxRepository;
import com.payflow.order.OrderRepository;
import com.payflow.order.OrderStatus;
import com.payflow.order.OrderWriter;
import com.payflow.order.client.CatalogClient;
import com.payflow.support.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderSagaOrchestratorTest {

    @Autowired
    private OrderWriter writer;
    @Autowired
    private OrderSagaOrchestrator saga;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OutboxRepository outbox;

    private UUID newOrder() {
        UUID product = UUID.randomUUID();
        return writer.create(UUID.randomUUID(), "key-" + UUID.randomUUID(), Map.of(product, 2),
                Map.of(product, new CatalogClient.ProductView(product, "S", "Thing", 750, 5))).id();
    }

    private List<String> sent(UUID orderId) {
        return outbox.findAll().stream().filter(m -> m.getMessageKey().equals(orderId.toString()))
                .map(OutboxMessage::getPayload).toList();
    }

    private OrderStatus status(UUID orderId) {
        return orders.findById(orderId).orElseThrow().getStatus();
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    @Test
    void happyPathConfirmsOrder() {
        UUID order = newOrder();
        assertThat(sent(order)).anySatisfy(p -> assertThat(p).contains("ChargePayment").contains("1500"));

        saga.handle(new OrderReply.PaymentCompleted(id(), order, UUID.randomUUID()));
        assertThat(status(order)).isEqualTo(OrderStatus.PAID);
        assertThat(sent(order)).anySatisfy(p -> assertThat(p).contains("ReserveStock"));

        saga.handle(new OrderReply.StockReserved(id(), order));
        assertThat(status(order)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(sent(order)).anySatisfy(p -> assertThat(p).contains("OrderConfirmed"));
    }

    @Test
    void stockFailureRefundsThenCancels() {
        UUID order = newOrder();
        saga.handle(new OrderReply.PaymentCompleted(id(), order, UUID.randomUUID()));
        saga.handle(new OrderReply.StockRejected(id(), order, "Not enough stock"));
        assertThat(status(order)).isEqualTo(OrderStatus.REFUNDING);
        assertThat(sent(order)).anySatisfy(p -> assertThat(p).contains("RefundPayment"));

        saga.handle(new OrderReply.PaymentRefunded(id(), order));
        assertThat(status(order)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sent(order)).anySatisfy(p -> assertThat(p).contains("OrderCancelled"));
    }

    @Test
    void duplicateReplyIsIgnoredAndLatePaymentIsRefunded() {
        UUID order = newOrder();
        var paid = new OrderReply.PaymentCompleted(id(), order, UUID.randomUUID());
        saga.handle(paid);
        saga.handle(paid);
        assertThat(sent(order).stream().filter(p -> p.contains("ReserveStock"))).hasSize(1);

        UUID late = newOrder();
        saga.cancelIfStillPending(late, "timed out");
        saga.handle(new OrderReply.PaymentCompleted(id(), late, UUID.randomUUID()));
        assertThat(status(late)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sent(late)).anySatisfy(p -> assertThat(p).contains("RefundPayment"));
    }
}
