package com.payflow.common.events;

// One topic per receiver. Messages are keyed by orderId, so all messages of one order land on the same
// partition and are consumed in order.
public final class Topics {

    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String INVENTORY_COMMANDS = "inventory.commands";
    public static final String ORDER_REPLIES = "order.replies";
    public static final String ORDER_EVENTS = "order.events";
    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }
}
