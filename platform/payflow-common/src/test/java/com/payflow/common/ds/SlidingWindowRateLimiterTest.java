package com.payflow.common.ds;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void allowsUpToLimitWithinWindowThenRejects() {
        var limiter = new SlidingWindowRateLimiter(3, Duration.ofMinutes(1), clock);
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        assertThat(limiter.rejectedCount()).isEqualTo(1);
    }

    @Test
    void windowSlidesSoOldRequestsStopCounting() {
        var limiter = new SlidingWindowRateLimiter(2, Duration.ofSeconds(60), clock);
        limiter.tryAcquire("ip");
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire("ip");
        assertThat(limiter.tryAcquire("ip")).isFalse();

        clock.advance(Duration.ofSeconds(31));
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
    }

    @Test
    void sweepForgetsIdleKeys() {
        var limiter = new SlidingWindowRateLimiter(5, Duration.ofSeconds(10), clock);
        limiter.tryAcquire("old");
        clock.advance(Duration.ofSeconds(11));
        limiter.tryAcquire("fresh");
        limiter.sweep(clock.millis());
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void keysAreIndependent() {
        var limiter = new SlidingWindowRateLimiter(1, Duration.ofMinutes(1), clock);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.trackedKeys()).isEqualTo(2);
    }
}
