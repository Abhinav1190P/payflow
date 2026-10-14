package com.payflow.fraud;

import com.payflow.wallet.Direction;
import com.payflow.wallet.LedgerEntry;
import com.payflow.wallet.LedgerTransaction;
import com.payflow.wallet.LedgerTransactionRepository;
import com.payflow.wallet.TransferCompletedEvent;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class FraudMonitor {

    private static final Logger log = LoggerFactory.getLogger(FraudMonitor.class);
    private static final Duration LOOKBACK = Duration.ofDays(7);

    private final TransferGraph graph = new TransferGraph();
    private final LedgerTransactionRepository transactions;
    private final FraudAlertStore store;
    private final Clock clock;

    public FraudMonitor(LedgerTransactionRepository transactions, FraudAlertStore store, Clock clock) {
        this.transactions = transactions;
        this.store = store;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadRecentTransfers() {
        List<LedgerTransaction> recent = transactions.findTransfersSince(clock.instant().minus(LOOKBACK));
        for (LedgerTransaction tx : recent) {
            UUID from = walletOf(tx, Direction.DEBIT);
            UUID to = walletOf(tx, Direction.CREDIT);
            graph.addEdge(from, to);
        }
        log.info("fraud graph loaded: {} wallets, {} edges", graph.nodeCount(), graph.edgeCount());
    }

    // Concept: @Async + after-commit event. Runs on another thread only once the transfer has committed,
    // so a slow fraud check never slows the transfer, and a rolled-back transfer never reaches the graph.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransfer(TransferCompletedEvent event) {
        graph.addEdgeDetectingCycle(event.fromWalletId(), event.toWalletId()).ifPresent(path -> {
            List<UUID> cycle = new ArrayList<>();
            cycle.add(event.fromWalletId());
            cycle.addAll(path);
            String walletPath = cycle.stream().map(UUID::toString).collect(Collectors.joining(" -> "));
            store.save(new FraudAlert(event.transactionId(), walletPath, clock.instant()));
            log.warn("possible circular transfer chain: {}", walletPath);
        });
    }

    public List<List<UUID>> scan() {
        return graph.findCycles();
    }

    public int edgeCount() {
        return graph.edgeCount();
    }

    private static UUID walletOf(LedgerTransaction tx, Direction direction) {
        return tx.getEntries().stream()
                .filter(e -> e.getDirection() == direction)
                .map(LedgerEntry::getWallet)
                .map(w -> w.getId())
                .findFirst()
                .orElseThrow();
    }
}
