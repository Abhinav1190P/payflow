package com.payflow.wallet;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

// Concept: binary search over a sorted snapshot list.
// The ledger stores the balance after every entry, in time order. "What was my balance at time T?"
// is the balance of the last entry at or before T. Because the times are sorted, binary search finds
// it in O(log n) instead of scanning all n entries. Building the arrays is O(n), memory O(n).
public final class BalanceHistory {

    private final long[] times;
    private final long[] balances;

    public BalanceHistory(List<BalancePoint> pointsInTimeOrder) {
        int n = pointsInTimeOrder.size();
        this.times = new long[n];
        this.balances = new long[n];
        for (int i = 0; i < n; i++) {
            var p = pointsInTimeOrder.get(i);
            times[i] = p.createdAt().toEpochMilli();
            balances[i] = p.balanceAfterMinor();
            if (i > 0 && times[i] < times[i - 1]) {
                throw new IllegalArgumentException("points must be sorted by time");
            }
        }
    }

    // Returns empty when T is before the first entry (the wallet had no activity yet).
    public OptionalLong balanceAt(Instant at) {
        int index = lastIndexAtOrBefore(at.toEpochMilli());
        return index < 0 ? OptionalLong.empty() : OptionalLong.of(balances[index]);
    }

    int lastIndexAtOrBefore(long target) {
        int lo = 0;
        int hi = times.length - 1;
        int answer = -1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (times[mid] <= target) {
                answer = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return answer;
    }

    public int size() {
        return times.length;
    }
}
