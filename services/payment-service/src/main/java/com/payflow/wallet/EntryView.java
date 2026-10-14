package com.payflow.wallet;

import java.time.Instant;
import java.util.UUID;

public record EntryView(UUID transactionId, TransactionType type, String reference, Direction direction,
                        long amountMinor, long balanceAfterMinor, Instant createdAt) {
}
