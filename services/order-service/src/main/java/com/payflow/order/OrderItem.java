package com.payflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

// The product name and price are COPIED into the order (a snapshot): if the catalog price changes tomorrow,
// yesterday's order must still show what the customer actually paid.
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    OrderItem(Order order, UUID productId, String productName, long unitPriceMinor, int quantity) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPriceMinor = unitPriceMinor;
        this.quantity = quantity;
    }

    public long lineTotalMinor() {
        return Math.multiplyExact(unitPriceMinor, (long) quantity);
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    public int getQuantity() {
        return quantity;
    }
}
