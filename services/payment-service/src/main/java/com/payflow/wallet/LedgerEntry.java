package com.payflow.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "balance_after_minor", nullable = false)
    private long balanceAfterMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(Builder b) {
        this.transaction = b.transaction;
        this.wallet = b.wallet;
        this.direction = b.direction;
        this.amountMinor = b.amountMinor;
        this.balanceAfterMinor = b.balanceAfterMinor;
        this.createdAt = b.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Concept: Builder pattern. Six fields, several of the same type (two longs): named builder steps
    // make the call site readable and build() validates everything in one place.
    public static final class Builder {
        private LedgerTransaction transaction;
        private Wallet wallet;
        private Direction direction;
        private long amountMinor;
        private long balanceAfterMinor;
        private Instant createdAt;

        private Builder() {
        }

        public Builder transaction(LedgerTransaction transaction) {
            this.transaction = transaction;
            return this;
        }

        public Builder wallet(Wallet wallet) {
            this.wallet = wallet;
            return this;
        }

        public Builder direction(Direction direction) {
            this.direction = direction;
            return this;
        }

        public Builder amountMinor(long amountMinor) {
            this.amountMinor = amountMinor;
            return this;
        }

        public Builder balanceAfterMinor(long balanceAfterMinor) {
            this.balanceAfterMinor = balanceAfterMinor;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public LedgerEntry build() {
            Objects.requireNonNull(transaction, "transaction");
            Objects.requireNonNull(wallet, "wallet");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(createdAt, "createdAt");
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return new LedgerEntry(this);
        }
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public long getBalanceAfterMinor() {
        return balanceAfterMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerEntry other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return LedgerEntry.class.hashCode();
    }
}
