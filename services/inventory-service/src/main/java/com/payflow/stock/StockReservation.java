package com.payflow.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockReservation() {
    }

    public StockReservation(UUID orderId, UUID productId, int quantity, Instant createdAt) {
        this.id = new Key(orderId, productId);
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public int getQuantity() {
        return quantity;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "order_id", nullable = false)
        private UUID orderId;

        @Column(name = "product_id", nullable = false)
        private UUID productId;

        protected Key() {
        }

        public Key(UUID orderId, UUID productId) {
            this.orderId = orderId;
            this.productId = productId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(orderId, k.orderId) && Objects.equals(productId, k.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, productId);
        }
    }
}
