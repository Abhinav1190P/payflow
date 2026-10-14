package com.payflow.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9-]{3,40}") String sku,
        @NotBlank @Size(max = 120) String name,
        @PositiveOrZero long priceMinor,
        @PositiveOrZero int stock) {
}
