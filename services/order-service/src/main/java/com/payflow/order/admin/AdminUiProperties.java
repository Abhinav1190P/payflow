package com.payflow.order.admin;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payflow.admin-ui")
public record AdminUiProperties(@NotBlank String username, @NotBlank String password) {
}
