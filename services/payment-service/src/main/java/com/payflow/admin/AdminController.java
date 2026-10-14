package com.payflow.admin;

import com.payflow.common.api.ApiResponse;
import com.payflow.common.api.PageResponse;
import com.payflow.fraud.FraudAlert;
import com.payflow.fraud.FraudAlertRepository;
import com.payflow.fraud.FraudMonitor;
import com.payflow.messaging.outbox.OutboxRepository;
import com.payflow.saga.PaymentRetryScheduler;
import com.payflow.wallet.WalletProperties;
import com.payflow.wallet.WalletRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final FraudMonitor fraudMonitor;
    private final FraudAlertRepository alerts;
    private final WalletRepository wallets;
    private final OutboxRepository outbox;
    private final PaymentRetryScheduler retries;
    private final WalletProperties properties;

    public AdminController(FraudMonitor fraudMonitor, FraudAlertRepository alerts, WalletRepository wallets,
                           OutboxRepository outbox, PaymentRetryScheduler retries, WalletProperties properties) {
        this.fraudMonitor = fraudMonitor;
        this.alerts = alerts;
        this.wallets = wallets;
        this.outbox = outbox;
        this.retries = retries;
        this.properties = properties;
    }

    public record FraudAlertView(UUID id, UUID transactionId, String walletPath, Instant detectedAt) {
        static FraudAlertView from(FraudAlert a) {
            return new FraudAlertView(a.getId(), a.getTransactionId(), a.getWalletPath(), a.getDetectedAt());
        }
    }

    public record Stats(long ledgerSumMinor, int transferGraphEdges, long outboxPending, int paymentRetriesPending) {
    }

    @GetMapping("/fraud-alerts")
    public ApiResponse<PageResponse<FraudAlertView>> fraudAlerts(
            @PageableDefault(size = 20, sort = "detectedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(PageResponse.from(alerts.findAll(pageable), FraudAlertView::from));
    }

    @PostMapping("/fraud/scan")
    public ApiResponse<List<List<UUID>>> scan() {
        return ApiResponse.of(fraudMonitor.scan());
    }

    // ledgerSumMinor must always be 0: every debit has a matching credit, so all balances together cancel out.
    @GetMapping("/stats")
    public ApiResponse<Stats> stats() {
        return ApiResponse.of(new Stats(wallets.sumBalances(properties.currency()), fraudMonitor.edgeCount(),
                outbox.countByPublishedAtIsNull(), retries.pending()));
    }
}
