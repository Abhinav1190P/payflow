package com.payflow.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.common.error.ConflictException;
import com.payflow.common.error.InsufficientFundsException;
import com.payflow.support.IntegrationTest;
import com.payflow.wallet.dto.TopUpRequest;
import com.payflow.wallet.dto.TransferRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

// Concept: concurrency proof. 100 threads are released at the same instant (CountDownLatch) and all try to
// send money from ONE wallet that can only afford half of them. With correct locking: exactly 50 succeed,
// 50 get "insufficient funds", the balance never goes negative, and nothing is lost or double-counted.
class WalletConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 100;
    private static final long AMOUNT = 100;
    private static final long STARTING_BALANCE = 50 * AMOUNT;

    @Autowired
    private WalletOpener opener;
    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private LedgerEntryRepository entries;

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        opener.existingOrOpen(id);
        return id;
    }

    private Wallet walletOf(UUID userId) {
        return wallets.findByUserIdAndCurrency(userId, "INR").orElseThrow();
    }

    @Test
    void hundredConcurrentTransfersNeverOverdrawOrLoseUpdates() throws Exception {
        UUID payer = newUser();
        UUID receiver = newUser();
        walletService.topUp(payer, "seed-" + UUID.randomUUID(), new TopUpRequest(STARTING_BALANCE));

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        walletService.transfer(payer, "k-" + UUID.randomUUID(), receiver,
                                new TransferRequest("receiver@payflow.dev", AMOUNT, "load"));
                        succeeded.incrementAndGet();
                    } catch (InsufficientFundsException e) {
                        insufficient.incrementAndGet();
                    } catch (Throwable t) {
                        unexpected.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(unexpected).isEmpty();
        assertThat(succeeded.get()).isEqualTo(50);
        assertThat(insufficient.get()).isEqualTo(50);

        Wallet payerWallet = walletOf(payer);
        Wallet receiverWallet = walletOf(receiver);
        assertThat(payerWallet.getBalanceMinor()).isZero();
        assertThat(receiverWallet.getBalanceMinor()).isEqualTo(STARTING_BALANCE);
        assertThat(entries.netForWallet(payerWallet.getId())).isEqualTo(payerWallet.getBalanceMinor());
        assertThat(entries.netForWallet(receiverWallet.getId())).isEqualTo(receiverWallet.getBalanceMinor());
        assertThat(payerWallet.getVersion()).isEqualTo(1 + 50);
    }

    @Test
    void sameIdempotencyKeyFromManyThreadsMovesMoneyOnce() throws Exception {
        UUID payer = newUser();
        UUID receiver = newUser();
        walletService.topUp(payer, "seed-" + UUID.randomUUID(), new TopUpRequest(10_000));
        String sharedKey = "same-key-" + UUID.randomUUID();
        var request = new TransferRequest("receiver@payflow.dev", 700, "once");

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger fresh = new AtomicInteger();
        AtomicInteger replayed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        var result = walletService.transfer(payer, sharedKey, receiver, request);
                        (result.replayed() ? replayed : fresh).incrementAndGet();
                    } catch (ConflictException | DataIntegrityViolationException e) {
                        rejected.incrementAndGet();
                    } catch (Throwable t) {
                        unexpected.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(unexpected).isEmpty();
        assertThat(fresh.get()).isEqualTo(1);
        assertThat(fresh.get() + replayed.get() + rejected.get()).isEqualTo(threads);
        assertThat(walletOf(payer).getBalanceMinor()).isEqualTo(10_000 - 700);
        assertThat(walletOf(receiver).getBalanceMinor()).isEqualTo(700);
        List<EntryView> payerEntries = entries.findViewsForWallet(walletOf(payer).getId(),
                java.time.Instant.EPOCH, java.time.Instant.now().plusSeconds(60));
        assertThat(payerEntries).hasSize(2);
    }
}
