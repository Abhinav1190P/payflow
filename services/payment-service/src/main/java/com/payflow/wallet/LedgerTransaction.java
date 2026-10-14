package com.payflow.wallet;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Concept: double-entry ledger. Every money movement is one transaction with at least two entries,
// and total debits must equal total credits. Money is never created or destroyed, only moved.
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Concept: OneToMany (lazy by default). Loading a page of transactions and then touching
    // getEntries() on each one is the classic N+1 query problem; see LedgerTransactionRepository.
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
    }

    public LedgerTransaction(TransactionType type, String reference, Instant createdAt) {
        this.type = type;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public void addEntry(LedgerEntry entry) {
        entries.add(entry);
    }

    // Concept: Streams partitioningBy + summingLong. Splits entries into debit/credit groups and sums each.
    public void assertBalanced() {
        Map<Boolean, Long> totals = entries.stream()
                .collect(Collectors.partitioningBy(e -> e.getDirection() == Direction.DEBIT,
                        Collectors.summingLong(LedgerEntry::getAmountMinor)));
        if (entries.size() < 2 || !totals.get(true).equals(totals.get(false))) {
            throw new IllegalStateException("unbalanced ledger transaction: " + totals);
        }
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerTransaction other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return LedgerTransaction.class.hashCode();
    }
}
