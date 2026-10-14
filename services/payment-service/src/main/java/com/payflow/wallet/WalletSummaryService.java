package com.payflow.wallet;

import com.payflow.wallet.dto.ReportRow;
import com.payflow.wallet.dto.TransactionView;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.dto.WalletSummary;
import com.payflow.wallet.report.ReportBucket;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

// Concept: CompletableFuture. The dashboard needs three independent reads. Running them in parallel on
// virtual threads makes the response take about as long as the slowest read, not the sum of all three.
@Service
public class WalletSummaryService {

    private final WalletQueryService queries;
    private final ExecutorService ioExecutor;
    private final Clock clock;

    public WalletSummaryService(WalletQueryService queries, @Qualifier("ioExecutor") ExecutorService ioExecutor, Clock clock) {
        this.queries = queries;
        this.ioExecutor = ioExecutor;
        this.clock = clock;
    }

    public WalletSummary summary(UUID userId) {
        Instant now = clock.instant();
        Instant monthStart = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        CompletableFuture<WalletResponse> wallet = CompletableFuture.supplyAsync(() -> queries.myWallet(userId), ioExecutor);
        CompletableFuture<List<TransactionView>> recent = CompletableFuture.supplyAsync(() ->
                queries.transactions(userId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))).items(), ioExecutor);
        CompletableFuture<List<ReportRow>> month = CompletableFuture.supplyAsync(() ->
                queries.report(userId, monthStart, now.plusSeconds(1), ReportBucket.MONTH), ioExecutor);

        try {
            return wallet.thenCombine(recent, (w, r) -> new WalletSummary(w, r, List.of()))
                    .thenCombine(month, (summary, m) -> new WalletSummary(summary.wallet(), summary.recent(), m))
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }
}
