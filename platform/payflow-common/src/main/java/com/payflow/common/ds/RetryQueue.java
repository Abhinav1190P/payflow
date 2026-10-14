package com.payflow.common.ds;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

// Concept: PriorityQueue (binary min-heap) as a retry scheduler.
// Failed work is queued with the time it may be retried next. The heap keeps the earliest "next attempt"
// at the head, so the scheduler only peeks at the head to know if anything is due: peek O(1),
// offer/poll O(log n). Backoff doubles each attempt (exponential backoff) up to a cap.
public class RetryQueue<T> {

    public record Task<T>(T payload, int attempt, Instant nextAttemptAt) {
    }

    private final PriorityQueue<Task<T>> heap = new PriorityQueue<>(Comparator.comparing(Task::nextAttemptAt));
    private final ReentrantLock lock = new ReentrantLock();
    private final Clock clock;
    private final Duration baseDelay;
    private final Duration maxDelay;

    public RetryQueue(Clock clock, Duration baseDelay, Duration maxDelay) {
        this.clock = clock;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    public Task<T> schedule(T payload, int attempt) {
        long factor = 1L << Math.min(attempt - 1, 20);
        Duration delay = baseDelay.multipliedBy(factor);
        if (delay.compareTo(maxDelay) > 0) {
            delay = maxDelay;
        }
        var task = new Task<>(payload, attempt, clock.instant().plus(delay));
        lock.lock();
        try {
            heap.offer(task);
        } finally {
            lock.unlock();
        }
        return task;
    }

    public List<Task<T>> pollDue() {
        Instant now = clock.instant();
        List<Task<T>> due = new ArrayList<>();
        lock.lock();
        try {
            while (!heap.isEmpty() && !heap.peek().nextAttemptAt().isAfter(now)) {
                due.add(heap.poll());
            }
        } finally {
            lock.unlock();
        }
        return due;
    }

    public int size() {
        lock.lock();
        try {
            return heap.size();
        } finally {
            lock.unlock();
        }
    }
}
