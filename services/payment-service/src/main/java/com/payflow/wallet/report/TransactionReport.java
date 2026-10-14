package com.payflow.wallet.report;

import com.payflow.wallet.Direction;
import com.payflow.wallet.EntryView;
import com.payflow.wallet.dto.ReportRow;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Concept: TreeMap for time-bucketed reports. A TreeMap keeps keys sorted (red-black tree), so buckets
// come out in date order with no extra sort, and range views like headMap/subMap are O(log n) to create.
// Big-O: building is O(n log b) for n entries into b buckets.
// Concept: Streams groupingBy into a TreeMap, with a nested partitioningBy (credits vs debits).
public final class TransactionReport {

    private TransactionReport() {
    }

    public static NavigableMap<LocalDate, Map<Boolean, Long>> totalsByBucket(List<EntryView> entries, ReportBucket bucket, ZoneId zone) {
        return entries.stream().collect(Collectors.groupingBy(
                e -> bucket.startOf(e.createdAt(), zone),
                TreeMap::new,
                Collectors.partitioningBy(e -> e.direction() == Direction.CREDIT,
                        Collectors.summingLong(EntryView::amountMinor))));
    }

    public static List<ReportRow> rows(List<EntryView> entries, ReportBucket bucket, ZoneId zone) {
        Map<LocalDate, Long> counts = entries.stream()
                .collect(Collectors.groupingBy(e -> bucket.startOf(e.createdAt(), zone), Collectors.counting()));
        return totalsByBucket(entries, bucket, zone).entrySet().stream()
                .map(en -> {
                    long credits = en.getValue().get(true);
                    long debits = en.getValue().get(false);
                    return new ReportRow(en.getKey(), credits, debits, credits - debits, counts.get(en.getKey()));
                })
                .toList();
    }
}
