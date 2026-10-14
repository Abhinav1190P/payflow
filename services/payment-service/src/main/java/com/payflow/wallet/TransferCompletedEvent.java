package com.payflow.wallet;

import java.time.Instant;
import java.util.UUID;

// Concept: Observer pattern via domain events. WalletService publishes this event and does not know who
// listens; the fraud monitor subscribes to it. Adding another listener needs no change in WalletService.
public record TransferCompletedEvent(UUID transactionId, UUID fromWalletId, UUID toWalletId, long amountMinor, Instant at) {
}
