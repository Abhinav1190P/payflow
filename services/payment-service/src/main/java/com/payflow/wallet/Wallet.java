package com.payflow.wallet;

import com.payflow.common.error.BusinessRuleException;
import com.payflow.common.error.InsufficientFundsException;
import com.payflow.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

// Concept: encapsulation + invariants. The balance is private and has no setter. The only way to
// change it is credit()/debit(), which enforce the rules: positive amounts, active wallet, and a user
// wallet can never go below zero. The database repeats the last rule as a CHECK constraint.
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The user lives in auth-service's database, so this service only stores the id (no foreign key across services).
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletType type;

    private String code;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    // Concept: optimistic locking. Hibernate adds "WHERE version = ?" to every UPDATE and bumps it.
    // If another transaction changed the row first, the update matches 0 rows and fails instead of
    // silently overwriting (no lost update).
    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Wallet() {
    }

    private Wallet(UUID userId, WalletType type, String code, String currency) {
        this.userId = userId;
        this.type = type;
        this.code = code;
        this.currency = currency;
        this.status = WalletStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static Wallet openFor(UUID userId, String currency) {
        return new Wallet(userId, WalletType.USER, null, currency);
    }

    public void credit(long amountMinor) {
        requirePositive(amountMinor);
        requireActive();
        balanceMinor = Direction.CREDIT.apply(balanceMinor, amountMinor);
    }

    public void debit(long amountMinor) {
        requirePositive(amountMinor);
        requireActive();
        if (!canDebit(amountMinor)) {
            throw new InsufficientFundsException();
        }
        balanceMinor = Direction.DEBIT.apply(balanceMinor, amountMinor);
    }

    public boolean canDebit(long amountMinor) {
        return type == WalletType.SYSTEM || balanceMinor >= amountMinor;
    }

    // A refund must always be able to return money, even to a frozen wallet; it re-activates only for the posting.
    void unfreezeForRefund() {
        status = WalletStatus.ACTIVE;
    }

    public void freeze() {
        status = WalletStatus.FROZEN;
    }

    public void unfreeze() {
        status = WalletStatus.ACTIVE;
    }

    public Money balance() {
        return Money.of(balanceMinor, currency);
    }

    private void requireActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new BusinessRuleException("WALLET_FROZEN", "Wallet is frozen");
        }
    }

    private static void requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Amount must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public WalletType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Wallet other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Wallet.class.hashCode();
    }
}
