package com.payflow.wallet.dto;

import java.util.List;

public record WalletSummary(WalletResponse wallet, List<TransactionView> recent, List<ReportRow> thisMonth) {
}
