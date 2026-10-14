package com.payflow.catalog.dto;

import com.payflow.catalog.Product;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, long priceMinor, int stock) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getPriceMinor(), p.getStock());
    }
}
