package com.payflow.wallet;

import com.payflow.common.error.BusinessRuleException;
import com.payflow.common.error.NotFoundException;
import com.payflow.common.error.PreconditionFailedException;
import com.payflow.idempotency.Fingerprint;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.idempotency.Replayable;
import com.payflow.wallet.dto.TopUpRequest;
import com.payflow.wallet.dto.TopUpResponse;
import com.payflow.wallet.dto.TransferRequest;
import com.payflow.wallet.dto.TransferResponse;
import com.payflow.wallet.dto.WalletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Concept: constructor injection. All dependencies are final and passed in the constructor, so the
// class cannot exist half-wired and is easy to construct in a unit test.
@Service
public class WalletService {

    public static final String CASH_IN = "CASH_IN";
    public static final String MERCHANT = "MERCHANT";

    private final WalletRepository wallets;
    private final LedgerTransactionRepository transactions;
    private final WalletOpener opener;
    private final IdempotencyService idempotency;
    private final ApplicationEventPublisher events;
    private final WalletProperties properties;
    private final Clock clock;

    public WalletService(WalletRepository wallets, LedgerTransactionRepository transactions, WalletOpener opener,
                         IdempotencyService idempotency, ApplicationEventPublisher events, WalletProperties properties, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.opener = opener;
        this.idempotency = idempotency;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Replayable<TopUpResponse> topUp(UUID userId, String idempotencyKey, TopUpRequest request) {
        long amount = request.amountMinor();
        if (amount > properties.maxTopUpMinor()) {
            throw new BusinessRuleException("TOP_UP_LIMIT", "A single top-up cannot exceed " + properties.maxTopUpMinor() + " minor units");
        }
        return idempotency.run(userId, idempotencyKey, Fingerprint.of("top-up", amount), TopUpResponse.class, () -> {
            UUID walletId = walletIdOf(userId);
            UUID cashInId = systemWalletId(CASH_IN);
            Map<UUID, Wallet> locked = lockInOrder(walletId, cashInId);
            Wallet wallet = locked.get(walletId);
            LedgerTransaction tx = post(TransactionType.TOP_UP, "top-up", locked.get(cashInId), wallet, amount);
            return Replayable.fresh(201, new TopUpResponse(tx.getId(), amount, wallet.getBalanceMinor(), tx.getCreatedAt()));
        });
    }

    // Concept: @Transactional isolation. READ_COMMITTED is enough here because correctness comes from the
    // row locks taken below, not from the isolation level. SERIALIZABLE would also be safe but makes
    // Postgres abort and force retries under contention.
    // The recipient id is resolved BEFORE this method (a remote call to auth-service): never hold a database
    // transaction open while waiting on the network.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Replayable<TransferResponse> transfer(UUID userId, String idempotencyKey, UUID recipientId, TransferRequest request) {
        String toEmail = request.toEmail().trim().toLowerCase(Locale.ROOT);
        String fingerprint = Fingerprint.of("transfer", toEmail, request.amountMinor(), request.note());
        return idempotency.run(userId, idempotencyKey, fingerprint, TransferResponse.class, () -> {
            UUID fromId = walletIdOf(userId);
            UUID toId = walletIdOf(recipientId);
            if (fromId.equals(toId)) {
                throw new BusinessRuleException("SELF_TRANSFER", "You cannot transfer to your own wallet");
            }
            Map<UUID, Wallet> locked = lockInOrder(fromId, toId);
            Wallet from = locked.get(fromId);
            LedgerTransaction tx = post(TransactionType.TRANSFER, request.note(), from, locked.get(toId), request.amountMinor());
            events.publishEvent(new TransferCompletedEvent(tx.getId(), fromId, toId, request.amountMinor(), tx.getCreatedAt()));
            return Replayable.fresh(201, new TransferResponse(tx.getId(), fromId, toId, request.amountMinor(),
                    from.getBalanceMinor(), tx.getCreatedAt()));
        });
    }

    // Used by the wallet payment method and the order saga. It returns an outcome instead of throwing for
    // "not enough money", because an exception passing through a @Transactional proxy marks the whole
    // transaction rollback-only, and the caller still needs to commit the declined result.
    @Transactional(propagation = Propagation.MANDATORY)
    public DebitOutcome tryPayMerchant(UUID userId, long amountMinor, String reference) {
        UUID payerId = walletIdOf(userId);
        UUID merchantId = systemWalletId(MERCHANT);
        Map<UUID, Wallet> locked = lockInOrder(payerId, merchantId);
        Wallet payer = locked.get(payerId);
        if (payer.getStatus() != WalletStatus.ACTIVE) {
            return new DebitOutcome.Rejected("WALLET_FROZEN", "Wallet is frozen");
        }
        if (!payer.canDebit(amountMinor)) {
            return new DebitOutcome.Rejected("INSUFFICIENT_FUNDS", "Wallet balance is too low for this amount");
        }
        LedgerTransaction tx = post(TransactionType.PAYMENT, reference, payer, locked.get(merchantId), amountMinor);
        return new DebitOutcome.Posted(tx, payer.getBalanceMinor());
    }

    // Saga compensation: money goes back from the merchant wallet to the user, as a new ledger transaction.
    // The original payment is never edited or deleted; a ledger is append-only.
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction refundFromMerchant(UUID userId, long amountMinor, String reference) {
        UUID userWalletId = walletIdOf(userId);
        UUID merchantId = systemWalletId(MERCHANT);
        Map<UUID, Wallet> locked = lockInOrder(userWalletId, merchantId);
        Wallet user = locked.get(userWalletId);
        if (user.getStatus() != WalletStatus.ACTIVE) {
            user.unfreezeForRefund();
        }
        return post(TransactionType.REFUND, reference, locked.get(merchantId), user, amountMinor);
    }

    // Concept: optimistic locking with HTTP ETag / If-Match. The admin sends the version they last saw.
    // A different version means someone changed the wallet since, so we refuse with 412 instead of
    // overwriting. If a change slips in between this check and the commit, @Version catches it (409).
    @Transactional
    public WalletResponse changeStatus(UUID walletId, WalletStatus target, long expectedVersion) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new NotFoundException("Wallet"));
        if (wallet.getVersion() != expectedVersion) {
            throw new PreconditionFailedException("Wallet version is " + wallet.getVersion() + ", you sent " + expectedVersion);
        }
        if (target == WalletStatus.FROZEN) {
            wallet.freeze();
        } else {
            wallet.unfreeze();
        }
        return WalletResponse.from(wallets.saveAndFlush(wallet));
    }

    // Concept: deadlock avoidance. Two transfers A->B and B->A that lock "from" first would each hold
    // one lock and wait forever for the other. Always locking in a fixed order (smaller id first) prevents it.
    private Map<UUID, Wallet> lockInOrder(UUID first, UUID second) {
        Map<UUID, Wallet> locked = new HashMap<>();
        Stream.of(first, second).sorted().forEach(id ->
                locked.put(id, wallets.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Wallet"))));
        return locked;
    }

    private LedgerTransaction post(TransactionType type, String reference, Wallet from, Wallet to, long amountMinor) {
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Wallets use different currencies");
        }
        from.debit(amountMinor);
        to.credit(amountMinor);
        Instant now = clock.instant();
        var tx = new LedgerTransaction(type, reference, now);
        tx.addEntry(LedgerEntry.builder().transaction(tx).wallet(from).direction(Direction.DEBIT)
                .amountMinor(amountMinor).balanceAfterMinor(from.getBalanceMinor()).createdAt(now).build());
        tx.addEntry(LedgerEntry.builder().transaction(tx).wallet(to).direction(Direction.CREDIT)
                .amountMinor(amountMinor).balanceAfterMinor(to.getBalanceMinor()).createdAt(now).build());
        tx.assertBalanced();
        return transactions.save(tx);
    }

    // Fast path: the wallet usually exists. Only the very first touch opens it in a separate transaction.
    private UUID walletIdOf(UUID userId) {
        return wallets.findIdByUserIdAndCurrency(userId, properties.currency()).orElseGet(() -> opener.ensureOrReread(userId));
    }

    private UUID systemWalletId(String code) {
        return wallets.findIdByCode(code).orElseThrow(() -> new IllegalStateException("system wallet missing: " + code));
    }
}
