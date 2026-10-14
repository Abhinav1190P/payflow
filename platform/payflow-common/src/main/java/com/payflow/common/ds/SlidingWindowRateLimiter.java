package com.payflow.common.ds;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Concept: sliding-window rate limiter with ArrayDeque.
// Each key keeps a queue of request timestamps. On every call, timestamps older than the window
// are removed from the head; if fewer than maxRequests remain, the new timestamp is added at the tail.
// ArrayDeque gives O(1) add at tail and remove at head (no node allocation, unlike LinkedList).
// Big-O: amortised O(1) per call (each timestamp is added once and removed once); memory O(maxRequests) per key.
//
// Concept: ConcurrentHashMap. Different keys are handled in parallel; computeIfAbsent is atomic, so two
// threads never create two deques for the same key.
// Concept: synchronized. Each deque is guarded by its own monitor, so only callers for the same key wait.
public class SlidingWindowRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong calls = new AtomicLong();

    public SlidingWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be at least 1");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        long now = clock.millis();
        if (calls.incrementAndGet() % 1024 == 0) {
            sweep(now);
        }
        Deque<Long> timestamps = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() < maxRequests) {
                timestamps.addLast(now);
                return true;
            }
        }
        rejected.incrementAndGet();
        return false;
    }

    // Drops keys whose newest request is older than the window, so memory does not grow with every IP ever seen.
    void sweep(long now) {
        windows.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                Long newest = timestamps.peekLast();
                return newest == null || newest <= now - windowMillis;
            }
        });
    }

    public long rejectedCount() {
        return rejected.get();
    }

    public int trackedKeys() {
        return windows.size();
    }
}
