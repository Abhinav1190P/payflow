package com.payflow.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payflow.rate-limit")
public record RateLimitProperties(@Min(1) int loginMaxRequests, @NotNull Duration loginWindow) {
}
