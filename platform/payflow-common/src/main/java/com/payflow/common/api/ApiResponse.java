package com.payflow.common.api;

import java.time.Instant;
import org.slf4j.MDC;

// Concept: generics. One envelope type works for any payload T, checked at compile time.
public record ApiResponse<T>(T data, String correlationId, Instant timestamp) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, MDC.get("correlationId"), Instant.now());
    }
}
