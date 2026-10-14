package com.payflow.wallet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotBlank @Email String toEmail,
        @Positive long amountMinor,
        @Size(max = 140) String note) {
}
