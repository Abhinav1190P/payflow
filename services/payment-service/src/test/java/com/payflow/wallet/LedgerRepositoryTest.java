package com.payflow.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

// Concept: @DataJpaTest. Only JPA, repositories, Flyway and a real Postgres (Testcontainers); every test
// runs in a transaction that is rolled back afterwards.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LedgerRepositoryTest {

    private static final int TRANSACTIONS = 10;

    @Autowired
    private EntityManager em;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private LedgerTransactionRepository transactions;
    @Autowired
    private LedgerEntryRepository entries;

    private Wallet wallet;
    private final List<UUID> txIds = new ArrayList<>();
    private final Instant base = Instant.parse("2026-04-01T08:00:00Z");

    @BeforeEach
    void seed() {
        wallet = wallets.save(Wallet.openFor(UUID.randomUUID(), "INR"));
        Wallet cashIn = wallets.findById(wallets.findIdByCode("CASH_IN").orElseThrow()).orElseThrow();
        for (int i = 0; i < TRANSACTIONS; i++) {
            Instant at = base.plusSeconds(60L * i);
            long amount = 100L * (i + 1);
            cashIn.debit(amount);
            wallet.credit(amount);
            var tx = new LedgerTransaction(TransactionType.TOP_UP, "seed-" + i, at);
            tx.addEntry(LedgerEntry.builder().transaction(tx).wallet(cashIn).direction(Direction.DEBIT)
                    .amountMinor(amount).balanceAfterMinor(cashIn.getBalanceMinor()).createdAt(at).build());
            tx.addEntry(LedgerEntry.builder().transaction(tx).wallet(wallet).direction(Direction.CREDIT)
                    .amountMinor(amount).balanceAfterMinor(wallet.getBalanceMinor()).createdAt(at).build());
            txIds.add(transactions.save(tx).getId());
        }
        em.flush();
        em.clear();
    }

    private Statistics stats() {
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    @Test
    void naiveLoadingCausesNPlusOneAndEntityGraphFixesIt() {
        Statistics stats = stats();

        stats.clear();
        List<LedgerTransaction> naive = transactions.findAllById(txIds);
        naive.forEach(tx -> tx.getEntries().size());
        long naiveQueries = stats.getPrepareStatementCount();

        em.clear();
        stats.clear();
        List<LedgerTransaction> fetched = transactions.findWithEntriesByIdIn(txIds);
        fetched.forEach(tx -> tx.getEntries().size());
        long fetchedQueries = stats.getPrepareStatementCount();

        assertThat(naive).hasSize(TRANSACTIONS);
        assertThat(fetched).hasSize(TRANSACTIONS).allSatisfy(tx -> assertThat(tx.getEntries()).hasSize(2));
        assertThat(naiveQueries).isEqualTo(1 + TRANSACTIONS);
        assertThat(fetchedQueries).isEqualTo(1);
    }

    @Test
    void pagesTransactionIdsNewestFirst() {
        var page = transactions.findIdsForWallet(wallet.getId(), PageRequest.of(0, 4, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(page.getTotalElements()).isEqualTo(TRANSACTIONS);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).containsExactly(txIds.get(9), txIds.get(8), txIds.get(7), txIds.get(6));
    }

    @Test
    void balanceHistoryAndNetMatchStoredBalance() {
        long expected = 100L * TRANSACTIONS * (TRANSACTIONS + 1) / 2;
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalanceMinor()).isEqualTo(expected);
        assertThat(entries.netForWallet(wallet.getId())).isEqualTo(expected);

        var history = new BalanceHistory(entries.findBalanceHistory(wallet.getId()));
        assertThat(history.size()).isEqualTo(TRANSACTIONS);
        assertThat(history.balanceAt(base.plusSeconds(90))).hasValue(300);
    }

    @Test
    void statementViewsComeBackInTimeOrderWithinRange() {
        var views = entries.findViewsForWallet(wallet.getId(), base.plusSeconds(60), base.plusSeconds(240));
        assertThat(views).extracting(EntryView::reference).containsExactly("seed-1", "seed-2", "seed-3");
    }
}
