package com.payflow.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// One row per order this service charged, so a refund knows exactly what to give back and a duplicate
// charge for the same order is impossible (order_id is the primary key).
@Entity
@Table(name = "order_payments")
public class OrderPayment {

    public enum Status { CHARGED, REFUNDED }

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "refund_transaction_id")
    private UUID refundTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderPayment() {
    }

    public OrderPayment(UUID orderId, UUID userId, long amountMinor, UUID transactionId, Instant createdAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.amountMinor = amountMinor;
        this.transactionId = transactionId;
        this.status = Status.CHARGED;
        this.createdAt = createdAt;
    }

    public void markRefunded(UUID refundTransactionId) {
        this.status = Status.REFUNDED;
        this.refundTransactionId = refundTransactionId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Status getStatus() {
        return status;
    }
}
