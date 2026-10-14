package com.payflow.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(nullable = false)
    private int stock;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String sku, String name, long priceMinor, int stock) {
        if (priceMinor < 0) {
            throw new IllegalArgumentException("price cannot be negative");
        }
        this.sku = sku;
        this.name = name;
        this.priceMinor = priceMinor;
        this.stock = Math.max(stock, 0);
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public long getPriceMinor() {
        return priceMinor;
    }

    public int getStock() {
        return stock;
    }

    // Encapsulation: stock can only go down through reserve(), never below zero (also a DB CHECK constraint).
    public boolean canReserve(int quantity) {
        return quantity > 0 && stock >= quantity;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("not enough stock for " + sku);
        }
        stock -= quantity;
    }

    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("stock cannot be negative");
        }
        this.stock = stock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Product.class.hashCode();
    }
}
