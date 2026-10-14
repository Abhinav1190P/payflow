package com.payflow.common.ds;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

// Concept: LRU cache built on LinkedHashMap.
// LinkedHashMap keeps a doubly linked list through its entries. With accessOrder=true every get()
// moves the entry to the tail, so the head is always the least recently used entry, and
// removeEldestEntry evicts it once size exceeds capacity.
// Big-O: get, put, evict are all O(1).
//
// Concept: ReentrantLock vs synchronized. Even get() mutates the linked list in access-order mode,
// so reads must also be locked. ReentrantLock is used (instead of synchronized) to show the explicit
// lock()/unlock() style, which also supports tryLock and timeouts when needed.
// Concept: AtomicLong. Hit/miss counters are updated without holding the lock, lock-free via CAS.
public class LruCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> map;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public LruCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCache.this.capacity;
            }
        };
    }

    public Optional<V> get(K key) {
        lock.lock();
        try {
            V value = map.get(key);
            (value == null ? misses : hits).incrementAndGet();
            return Optional.ofNullable(value);
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> loader) {
        Optional<V> cached = get(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        V loaded = loader.apply(key);
        if (loaded != null) {
            put(key, loaded);
        }
        return loaded;
    }

    public void invalidate(K key) {
        lock.lock();
        try {
            map.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            map.clear();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }
}
