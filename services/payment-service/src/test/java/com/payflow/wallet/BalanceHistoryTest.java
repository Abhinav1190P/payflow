package com.payflow.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceHistoryTest {

    private final Instant t0 = Instant.parse("2026-03-01T10:00:00Z");

    private BalanceHistory history() {
        return new BalanceHistory(List.of(
                new BalancePoint(t0, 1000),
                new BalancePoint(t0.plusSeconds(60), 700),
                new BalancePoint(t0.plusSeconds(120), 1700)));
    }

    @Test
    void returnsBalanceOfLastEntryAtOrBeforeTime() {
        assertThat(history().balanceAt(t0)).hasValue(1000);
        assertThat(history().balanceAt(t0.plusSeconds(59))).hasValue(1000);
        assertThat(history().balanceAt(t0.plusSeconds(60))).hasValue(700);
        assertThat(history().balanceAt(t0.plusSeconds(3600))).hasValue(1700);
    }

    @Test
    void emptyBeforeFirstEntryAndForEmptyHistory() {
        assertThat(history().balanceAt(t0.minusSeconds(1))).isEmpty();
        assertThat(new BalanceHistory(List.of()).balanceAt(t0)).isEmpty();
    }

    @Test
    void agreesWithLinearScanOnLargeInput() {
        List<BalancePoint> points = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            points.add(new BalancePoint(t0.plusSeconds(i * 3L), i));
        }
        var history = new BalanceHistory(points);
        for (long probe = -5; probe < 30_010; probe += 7) {
            Instant at = t0.plusSeconds(probe);
            long expected = -1;
            for (BalancePoint p : points) {
                if (!p.createdAt().isAfter(at)) {
                    expected = p.balanceAfterMinor();
                }
            }
            var actual = history.balanceAt(at);
            if (expected < 0) {
                assertThat(actual).isEmpty();
            } else {
                assertThat(actual).hasValue(expected);
            }
        }
    }

    @Test
    void rejectsUnsortedInput() {
        assertThatThrownBy(() -> new BalanceHistory(List.of(new BalancePoint(t0, 1), new BalancePoint(t0.minusSeconds(1), 2))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
