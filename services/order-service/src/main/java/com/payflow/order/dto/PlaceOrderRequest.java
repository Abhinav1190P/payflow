package com.payflow.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(@NotEmpty @Size(max = 20) List<@Valid Line> items) {

    public record Line(@NotNull UUID productId, @Min(1) @Max(100) int quantity) {
    }
}
