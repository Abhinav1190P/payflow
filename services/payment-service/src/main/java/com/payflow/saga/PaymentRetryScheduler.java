package com.payflow.saga;

import com.payflow.common.ds.RetryQueue;
import com.payflow.common.events.PaymentCommand;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Concept: PriorityQueue retry scheduler. A charge that failed for a TECHNICAL reason (lock timeout, a dropped
// connection: Spring's TransientDataAccessException) is not a business "no". It is parked in a RetryQueue
// ordered by next-attempt time and retried with exponential backoff. After MAX_ATTEMPTS the saga gets a
// PaymentFailed reply. Trade-off: the queue is in memory, so a restart loses pending retries; order-service's
// saga timeout then cancels those orders.
@Component
public class PaymentRetryScheduler {

    static final int MAX_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(PaymentRetryScheduler.class);

    private final RetryQueue<PaymentCommand> queue;
    private final OrderPaymentService payments;

    public PaymentRetryScheduler(OrderPaymentService payments, Clock clock) {
        this.payments = payments;
        this.queue = new RetryQueue<>(clock, Duration.ofMillis(500), Duration.ofSeconds(15));
    }

    public void schedule(PaymentCommand command, int attempt) {
        queue.schedule(command, attempt);
        log.warn("payment for order {} parked for retry, attempt {}", command.orderId(), attempt);
    }

    @Scheduled(fixedDelay = 250)
    public void runDue() {
        for (RetryQueue.Task<PaymentCommand> task : queue.pollDue()) {
            try {
                dispatch(task.payload());
            } catch (TransientDataAccessException e) {
                if (task.attempt() >= MAX_ATTEMPTS && task.payload() instanceof PaymentCommand.ChargePayment charge) {
                    payments.failAfterRetries(charge);
                } else {
                    schedule(task.payload(), task.attempt() + 1);
                }
            }
        }
    }

    void dispatch(PaymentCommand command) {
        switch (command) {
            case PaymentCommand.ChargePayment charge -> payments.charge(charge);
            case PaymentCommand.RefundPayment refund -> payments.refund(refund);
        }
    }

    public int pending() {
        return queue.size();
    }
}
