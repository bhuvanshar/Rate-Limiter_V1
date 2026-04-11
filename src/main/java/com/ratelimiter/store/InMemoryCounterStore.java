package com.ratelimiter.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of CounterStore using ConcurrentHashMap.
 *
 * Thread safety analysis:
 * - ConcurrentHashMap.computeIfAbsent is atomic per key (the lambda runs
 *   at most once per key). This means two concurrent requests for the same
 *   key will share the same state object — no duplicates.
 * - Within each state object (TokenBucketState / SlidingWindowState), all
 *   mutations use AtomicLong CAS operations — no locks needed.
 * - The eviction scan iterates a snapshot of the key set. ConcurrentHashMap
 *   supports concurrent reads during iteration.
 *
 * Memory estimate: ~200 bytes per entry (key ~80 bytes, state ~40 bytes,
 * CHM node overhead ~80 bytes). For 100K entries: ~20MB.
 */
@Component
public class InMemoryCounterStore implements CounterStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCounterStore.class);
    private static final long ESTIMATED_BYTES_PER_ENTRY = 200L;

    private final ConcurrentHashMap<String, TokenBucketState> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlidingWindowState> windows = new ConcurrentHashMap<>();

    @Override
    public TokenBucketState getOrCreateBucket(String key, long capacity) {
        return buckets.computeIfAbsent(key, k -> new TokenBucketState(capacity));
    }

    @Override
    public SlidingWindowState getOrCreateWindow(String key) {
        return windows.computeIfAbsent(key, k -> new SlidingWindowState());
    }

    @Override
    public int evictExpired(long maxIdleMs) {
        long now = System.currentTimeMillis();
        int evicted = 0;

        var bucketIterator = buckets.entrySet().iterator();
        while (bucketIterator.hasNext()) {
            var entry = bucketIterator.next();
            if (now - entry.getValue().getLastAccessTimeMs() > maxIdleMs) {
                bucketIterator.remove();
                evicted++;
            }
        }

        var windowIterator = windows.entrySet().iterator();
        while (windowIterator.hasNext()) {
            var entry = windowIterator.next();
            if (now - entry.getValue().getLastAccessTimeMs() > maxIdleMs) {
                windowIterator.remove();
                evicted++;
            }
        }

        if (evicted > 0) {
            log.info("Evicted {} expired counter entries", evicted);
        }
        return evicted;
    }

    @Override
    public long size() {
        return buckets.size() + windows.size();
    }

    @Override
    public long estimateMemoryBytes() {
        return size() * ESTIMATED_BYTES_PER_ENTRY;
    }
}
