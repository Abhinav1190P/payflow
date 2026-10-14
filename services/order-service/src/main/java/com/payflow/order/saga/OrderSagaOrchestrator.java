package com.payflow.order.saga;

import com.payflow.common.events.InventoryCommand;
import com.payflow.common.events.OrderEvent;
import com.payflow.common.events.OrderReply;
import com.payflow.common.events.PaymentCommand;
import com.payflow.common.events.Topics;
import com.payflow.messaging.inbox.Inbox;
import com.payflow.messaging.outbox.OutboxWriter;
import com.payflow.order.Order;
import com.payflow.order.OrderMetrics;
import com.payflow.order.OrderRepository;
import com.payflow.order.OrderStatus;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Concept: Saga (orchestration). An order spans two other services' databases, so one ACID transaction is
// impossible. Instead each step is a local transaction, and every failure has a COMPENSATING action:
//   1. charge payment            -> on failure: cancel the order
//   2. reserve stock             -> on failure: refund the payment (compensation), then cancel
// The orchestrator reacts to each reply: update the order (state machine) + send the next command, in ONE
// local transaction together with the inbox entry and the outbox row.
@Service
public class OrderSagaOrchestrator {

    static final String CONSUMER = "order-saga";
    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orders;
    private final Inbox inbox;
    private final OutboxWriter outbox;
    private final OrderMetrics metrics;
    private final Clock clock;

    public OrderSagaOrchestrator(OrderRepository orders, Inbox inbox, OutboxWriter outbox, OrderMetrics metrics, Clock clock) {
        this.orders = orders;
        this.inbox = inbox;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void handle(OrderReply reply) {
        if (!inbox.firstTime(CONSUMER, reply.messageId())) {
            return;
        }
        Order order = orders.findWithItems(reply.orderId()).orElse(null);
        if (order == null) {
            log.warn("reply {} for unknown order {}", reply.getClass().getSimpleName(), reply.orderId());
            return;
        }
        switch (reply) {
            case OrderReply.PaymentCompleted paid -> onPaid(order);
            case OrderReply.PaymentFailed failed -> {
                if (order.getStatus() == OrderStatus.PENDING) {
                    cancel(order, failed.reason());
                }
            }
            case OrderReply.StockReserved reserved -> {
                if (order.getStatus() == OrderStatus.PAID) {
                    order.moveTo(OrderStatus.CONFIRMED, clock.instant());
                    metrics.reached(OrderStatus.CONFIRMED);
                    publishEvent(order, new OrderEvent.OrderConfirmed(newId(), order.getId(), order.getUserId(), order.getTotalMinor()));
                }
            }
            case OrderReply.StockRejected rejected -> {
                if (order.getStatus() == OrderStatus.PAID) {
                    order.startRefund(rejected.reason(), clock.instant());
                    metrics.reached(OrderStatus.REFUNDING);
                    refund(order);
                }
            }
            case OrderReply.PaymentRefunded refunded -> {
                if (order.getStatus() == OrderStatus.REFUNDING) {
                    order.moveTo(OrderStatus.CANCELLED, clock.instant());
                    metrics.reached(OrderStatus.CANCELLED);
                    publishEvent(order, new OrderEvent.OrderCancelled(newId(), order.getId(), order.getUserId(), order.getCancelReason()));
                }
            }
        }
    }

    // A payment can arrive AFTER the saga timeout already cancelled the order. The customer was charged for an
    // order that no longer exists, so the compensation is to refund straight away.
    private void onPaid(Order order) {
        if (order.getStatus() == OrderStatus.PENDING) {
            order.moveTo(OrderStatus.PAID, clock.instant());
            metrics.reached(OrderStatus.PAID);
            var lines = order.getItems().stream()
                    .map(i -> new InventoryCommand.Line(i.getProductId(), i.getQuantity()))
                    .toList();
            outbox.enqueue(Topics.INVENTORY_COMMANDS, order.getId().toString(),
                    new InventoryCommand.ReserveStock(newId(), order.getId(), lines));
        } else if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("late payment for cancelled order {}, refunding", order.getId());
            refund(order);
        }
    }

    @Transactional
    public void cancelIfStillPending(UUID orderId, String reason) {
        orders.findById(orderId)
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .ifPresent(o -> cancel(o, reason));
    }

    private void cancel(Order order, String reason) {
        order.cancel(reason, clock.instant());
        metrics.reached(OrderStatus.CANCELLED);
        publishEvent(order, new OrderEvent.OrderCancelled(newId(), order.getId(), order.getUserId(), reason));
    }

    private void refund(Order order) {
        outbox.enqueue(Topics.PAYMENT_COMMANDS, order.getId().toString(),
                new PaymentCommand.RefundPayment(newId(), order.getId(), order.getUserId(), order.getTotalMinor()));
    }

    private void publishEvent(Order order, OrderEvent event) {
        outbox.enqueue(Topics.ORDER_EVENTS, order.getId().toString(), event);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
