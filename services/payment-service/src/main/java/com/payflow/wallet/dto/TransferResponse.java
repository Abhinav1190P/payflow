package com.payflow.wallet.dto;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID transactionId, UUID fromWalletId, UUID toWalletId, long amountMinor,
                               long balanceAfterMinor, Instant createdAt) {
}
