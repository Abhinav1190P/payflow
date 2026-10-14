package com.payflow.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(UUID userId, String idempotencyKey, Instant now) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addItem(UUID productId, String productName, long unitPriceMinor, int quantity) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("items can only be added while the order is pending");
        }
        var item = new OrderItem(this, productId, productName, unitPriceMinor, quantity);
        items.add(item);
        totalMinor = Math.addExact(totalMinor, item.lineTotalMinor());
    }

    public void moveTo(OrderStatus next, Instant now) {
        this.status = status.moveTo(next);
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        moveTo(OrderStatus.CANCELLED, now);
        this.cancelReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 200));
    }

    public void startRefund(String reason, Instant now) {
        moveTo(OrderStatus.REFUNDING, now);
        this.cancelReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 200));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Order other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Order.class.hashCode();
    }
}
