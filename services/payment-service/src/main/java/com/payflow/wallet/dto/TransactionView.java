package com.payflow.wallet.dto;

import com.payflow.wallet.Direction;
import com.payflow.wallet.LedgerEntry;
import com.payflow.wallet.LedgerTransaction;
import com.payflow.wallet.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record TransactionView(UUID id, TransactionType type, String reference, Direction direction, long amountMinor,
                              long balanceAfterMinor, UUID counterpartyWalletId, Instant createdAt) {

    // Shows the transaction from one wallet's point of view: my entry, plus the other side's wallet id.
    public static TransactionView of(LedgerTransaction tx, UUID walletId) {
        LedgerEntry mine = tx.getEntries().stream()
                .filter(e -> e.getWallet().getId().equals(walletId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("transaction does not touch wallet " + walletId));
        UUID counterparty = tx.getEntries().stream()
                .map(e -> e.getWallet().getId())
                .filter(id -> !id.equals(walletId))
                .findFirst()
                .orElse(null);
        return new TransactionView(tx.getId(), tx.getType(), tx.getReference(), mine.getDirection(), mine.getAmountMinor(),
                mine.getBalanceAfterMinor(), counterparty, tx.getCreatedAt());
    }
}
