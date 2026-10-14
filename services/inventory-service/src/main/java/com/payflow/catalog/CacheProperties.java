package com.payflow.catalog;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payflow.cache")
public record CacheProperties(@Min(1) int autocompleteCapacity) {
}
