package com.payflow.common.ds;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LruCacheTest {

    @Test
    void evictsLeastRecentlyUsedEntryWhenFull() {
        var cache = new LruCache<String, Integer>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");
        cache.put("c", 3);

        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("a")).contains(1);
        assertThat(cache.get("c")).contains(3);
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void countsHitsAndMisses() {
        var cache = new LruCache<String, Integer>(4);
        cache.put("x", 1);
        cache.get("x");
        cache.get("x");
        cache.get("missing");
        assertThat(cache.hits()).isEqualTo(2);
        assertThat(cache.misses()).isEqualTo(1);
    }

    @Test
    void computeIfAbsentLoadsOnceThenServesFromCache() {
        var cache = new LruCache<Integer, String>(4);
        int[] loads = {0};
        cache.computeIfAbsent(1, k -> "v" + (++loads[0]));
        String second = cache.computeIfAbsent(1, k -> "v" + (++loads[0]));
        assertThat(second).isEqualTo("v1");
        assertThat(loads[0]).isEqualTo(1);
    }

    @Test
    void staysWithinCapacityUnderConcurrentWrites() throws InterruptedException {
        var cache = new LruCache<Integer, Integer>(50);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            int base = t * 1000;
            pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    cache.put(base + i, i);
                    cache.get(base + i / 2);
                }
                done.countDown();
            });
        }
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(cache.size()).isEqualTo(50);
    }
}
