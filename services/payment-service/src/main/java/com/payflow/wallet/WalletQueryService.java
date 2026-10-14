package com.payflow.wallet;

import com.payflow.common.api.PageResponse;

import com.payflow.common.error.InvalidRequestException;
import com.payflow.common.error.NotFoundException;
import com.payflow.wallet.dto.BalanceAtResponse;
import com.payflow.wallet.dto.ReportRow;
import com.payflow.wallet.dto.TransactionView;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.report.ReportBucket;
import com.payflow.wallet.report.TransactionReport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WalletQueryService {

    private final WalletRepository wallets;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final WalletOpener opener;

    public WalletQueryService(WalletRepository wallets, LedgerTransactionRepository transactions,
                              LedgerEntryRepository entries, WalletOpener opener) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.entries = entries;
        this.opener = opener;
    }

    public WalletResponse myWallet(UUID userId) {
        return wallets.findById(walletId(userId))
                .map(WalletResponse::from)
                .orElseThrow(() -> new NotFoundException("Wallet"));
    }

    public WalletResponse wallet(UUID walletId) {
        return wallets.findById(walletId).map(WalletResponse::from).orElseThrow(() -> new NotFoundException("Wallet"));
    }

    public PageResponse<TransactionView> transactions(UUID userId, Pageable pageable) {
        UUID walletId = walletId(userId);
        Page<UUID> ids = transactions.findIdsForWallet(walletId, safeSort(pageable));
        if (ids.isEmpty()) {
            return PageResponse.of(List.of(), ids);
        }
        Map<UUID, LedgerTransaction> byId = transactions.findWithEntriesByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(LedgerTransaction::getId, Function.identity()));
        List<TransactionView> views = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(tx -> TransactionView.of(tx, walletId))
                .toList();
        return PageResponse.of(views, ids);
    }

    public List<EntryView> statement(UUID userId, Instant from, Instant to) {
        return entries.findViewsForWallet(walletId(userId), from, to);
    }

    public BalanceAtResponse balanceAt(UUID userId, Instant at) {
        var history = new BalanceHistory(entries.findBalanceHistory(walletId(userId)));
        var balance = history.balanceAt(at);
        return new BalanceAtResponse(at, balance.orElse(0), balance.isPresent());
    }

    public List<ReportRow> report(UUID userId, Instant from, Instant to, ReportBucket bucket) {
        return TransactionReport.rows(entries.findViewsForWallet(walletId(userId), from, to), bucket, ZoneOffset.UTC);
    }

    // Only createdAt may be sorted on (the query is hand-written JPQL, so Spring cannot check the property),
    // and id is added as a tie-breaker so pages stay stable when two rows share a timestamp.
    private static Pageable safeSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!order.getProperty().equals("createdAt")) {
                throw new InvalidRequestException("Transactions can only be sorted by createdAt");
            }
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        Sort.Direction direction = sort.getOrderFor("createdAt").getDirection();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by(direction, "id")));
    }

    private UUID walletId(UUID userId) {
        return opener.existingOrOpen(userId);
    }
}
