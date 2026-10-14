package com.payflow.common.ds;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryQueueTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RetryQueue<String> queue = new RetryQueue<>(clock, Duration.ofSeconds(1), Duration.ofSeconds(30));

    @Test
    void releasesTasksInNextAttemptOrderOnlyWhenDue() {
        queue.schedule("third-attempt", 3);
        queue.schedule("first-attempt", 1);
        queue.schedule("second-attempt", 2);

        assertThat(queue.pollDue()).isEmpty();
        clock.advance(Duration.ofSeconds(2));
        assertThat(queue.pollDue()).extracting(RetryQueue.Task::payload).containsExactly("first-attempt", "second-attempt");
        clock.advance(Duration.ofSeconds(2));
        assertThat(queue.pollDue()).extracting(RetryQueue.Task::payload).containsExactly("third-attempt");
        assertThat(queue.size()).isZero();
    }

    @Test
    void backoffDoublesAndIsCapped() {
        Instant start = clock.instant();
        assertThat(queue.schedule("a", 1).nextAttemptAt()).isEqualTo(start.plusSeconds(1));
        assertThat(queue.schedule("b", 4).nextAttemptAt()).isEqualTo(start.plusSeconds(8));
        assertThat(queue.schedule("c", 30).nextAttemptAt()).isEqualTo(start.plusSeconds(30));
    }
}
