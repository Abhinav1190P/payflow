package com.payflow.wallet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payflow.wallet")
public record WalletProperties(@NotBlank @Size(min = 3, max = 3) String currency, @Min(1) long maxTopUpMinor) {
}
