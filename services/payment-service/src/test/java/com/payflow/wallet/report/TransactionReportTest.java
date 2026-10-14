package com.payflow.wallet.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.wallet.Direction;
import com.payflow.wallet.EntryView;
import com.payflow.wallet.TransactionType;
import com.payflow.wallet.dto.ReportRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionReportTest {

    private static EntryView entry(String at, Direction direction, long amount) {
        return new EntryView(UUID.randomUUID(), TransactionType.TRANSFER, null, direction, amount, 0, Instant.parse(at));
    }

    private final List<EntryView> entries = List.of(
            entry("2026-02-03T09:00:00Z", Direction.CREDIT, 1000),
            entry("2026-02-01T09:00:00Z", Direction.CREDIT, 500),
            entry("2026-02-01T18:00:00Z", Direction.DEBIT, 200),
            entry("2026-03-15T12:00:00Z", Direction.DEBIT, 50));

    @Test
    void groupsByDayInDateOrder() {
        List<ReportRow> rows = TransactionReport.rows(entries, ReportBucket.DAY, ZoneOffset.UTC);
        assertThat(rows).extracting(ReportRow::bucketStart).containsExactly(
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-03"), LocalDate.parse("2026-03-15"));
        assertThat(rows.get(0)).isEqualTo(new ReportRow(LocalDate.parse("2026-02-01"), 500, 200, 300, 2));
    }

    @Test
    void groupsByMonth() {
        List<ReportRow> rows = TransactionReport.rows(entries, ReportBucket.MONTH, ZoneOffset.UTC);
        assertThat(rows).containsExactly(
                new ReportRow(LocalDate.parse("2026-02-01"), 1500, 200, 1300, 3),
                new ReportRow(LocalDate.parse("2026-03-01"), 0, 50, -50, 1));
    }

    @Test
    void treeMapSupportsRangeViews() {
        var byDay = TransactionReport.totalsByBucket(entries, ReportBucket.DAY, ZoneOffset.UTC);
        assertThat(byDay.headMap(LocalDate.parse("2026-03-01"))).hasSize(2);
        assertThat(byDay.floorKey(LocalDate.parse("2026-02-02"))).isEqualTo(LocalDate.parse("2026-02-01"));
    }

    @Test
    void emptyInputGivesEmptyReport() {
        assertThat(TransactionReport.rows(List.of(), ReportBucket.DAY, ZoneOffset.UTC)).isEmpty();
    }
}
