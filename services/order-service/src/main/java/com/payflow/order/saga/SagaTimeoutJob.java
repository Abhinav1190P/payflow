package com.payflow.order.saga;

import com.payflow.order.Order;
import com.payflow.order.OrderRepository;
import com.payflow.order.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Concept: @Scheduled. A saga must never hang forever (lost message, a participant down for hours).
// Orders still PENDING after the timeout are cancelled; if the payment shows up later, the orchestrator refunds it.
@Component
public class SagaTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutJob.class);

    private final OrderRepository orders;
    private final OrderSagaOrchestrator orchestrator;
    private final Clock clock;
    private final Duration timeout;

    public SagaTimeoutJob(OrderRepository orders, OrderSagaOrchestrator orchestrator, Clock clock,
                          @Value("${payflow.saga.timeout:PT2M}") Duration timeout) {
        this.orders = orders;
        this.orchestrator = orchestrator;
        this.clock = clock;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${payflow.saga.sweep-ms:30000}")
    public void cancelStuckOrders() {
        var stuck = orders.findStuck(OrderStatus.PENDING, clock.instant().minus(timeout), PageRequest.of(0, 100));
        for (Order order : stuck) {
            log.warn("order {} stuck in PENDING, cancelling", order.getId());
            orchestrator.cancelIfStillPending(order.getId(), "Payment did not complete in time");
        }
    }
}
