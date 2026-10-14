package com.payflow.order.client;

import com.payflow.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", path = "/api/v1/products")
public interface CatalogClient {

    @GetMapping("/batch")
    ApiResponse<List<ProductView>> batch(@RequestParam("ids") List<UUID> ids);

    record ProductView(UUID id, String sku, String name, long priceMinor, int stock) {
    }
}
