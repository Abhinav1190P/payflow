package com.payflow.wallet.dto;

import java.time.Instant;
import java.util.UUID;

public record TopUpResponse(UUID transactionId, long amountMinor, long balanceAfterMinor, Instant createdAt) {
}
