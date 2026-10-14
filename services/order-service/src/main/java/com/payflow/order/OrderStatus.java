package com.payflow.order;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// Concept: enum with behaviour as a state machine. The allowed transitions live in the enum itself, so
// no service can move an order from CANCELLED back to PAID by mistake: moveTo() refuses illegal steps.
//
//   PENDING --PaymentCompleted--> PAID --StockReserved--> CONFIRMED
//      |                           |
//      +--PaymentFailed/timeout--> CANCELLED <--PaymentRefunded-- REFUNDING <--StockRejected--+
public enum OrderStatus {
    PENDING,
    PAID,
    CONFIRMED,
    REFUNDING,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(PAID, CANCELLED));
        ALLOWED.put(PAID, EnumSet.of(CONFIRMED, REFUNDING));
        ALLOWED.put(REFUNDING, EnumSet.of(CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canMoveTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public OrderStatus moveTo(OrderStatus next) {
        if (!canMoveTo(next)) {
            throw new IllegalStateException("order cannot go from " + this + " to " + next);
        }
        return next;
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
