package com.payflow.catalog;

import com.payflow.catalog.dto.CreateProductRequest;
import com.payflow.catalog.dto.ProductResponse;
import com.payflow.common.api.ApiResponse;
import com.payflow.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    public record StockRequest(@PositiveOrZero int stock) {
    }

    @GetMapping("/products")
    public ApiResponse<PageResponse<ProductResponse>> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiResponse.of(productService.page(pageable));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(productService.byId(id));
    }

    // Used by order-service's checkout (OpenFeign) to price a cart in one call.
    @GetMapping("/products/batch")
    public ApiResponse<List<ProductResponse>> batch(@RequestParam("ids") @Size(min = 1, max = 50) List<UUID> ids) {
        return ApiResponse.of(productService.byIds(ids));
    }

    @GetMapping("/products/autocomplete")
    public ApiResponse<List<String>> autocomplete(@RequestParam("q") @NotBlank @Size(max = 40) String prefix,
                                                  @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return ApiResponse.of(productService.autocomplete(prefix, limit));
    }

    @PostMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.of(productService.create(request));
    }

    @PutMapping("/admin/products/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> setStock(@PathVariable UUID id, @Valid @RequestBody StockRequest request) {
        return ApiResponse.of(productService.setStock(id, request.stock()));
    }
}
