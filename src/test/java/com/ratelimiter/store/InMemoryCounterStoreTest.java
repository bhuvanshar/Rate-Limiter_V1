package com.ratelimiter.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCounterStoreTest {

    private InMemoryCounterStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCounterStore();
    }

    @Test
    void shouldCreateAndRetrieveBucket() {
        TokenBucketState bucket = store.getOrCreateBucket("key1", 100);
        assertNotNull(bucket);

        // Second call should return the same instance
        TokenBucketState same = store.getOrCreateBucket("key1", 100);
        assertSame(bucket, same);
    }

    @Test
    void shouldCreateAndRetrieveWindow() {
        SlidingWindowState window = store.getOrCreateWindow("key1");
        assertNotNull(window);

        SlidingWindowState same = store.getOrCreateWindow("key1");
        assertSame(window, same);
    }

    @Test
    void shouldTrackSize() {
        assertEquals(0, store.size());

        store.getOrCreateBucket("b1", 10);
        assertEquals(1, store.size());

        store.getOrCreateWindow("w1");
        assertEquals(2, store.size());

        store.getOrCreateBucket("b2", 10);
        assertEquals(3, store.size());
    }

    @Test
    void shouldEvictExpiredEntries() throws InterruptedException {
        store.getOrCreateBucket("old-key", 10);
        Thread.sleep(50);

        // Evict entries idle for more than 20ms
        int evicted = store.evictExpired(20);
        assertEquals(1, evicted);
        assertEquals(0, store.size());
    }

    @Test
    void shouldNotEvictRecentEntries() {
        store.getOrCreateBucket("new-key", 10);

        // Evict entries idle for more than 10 seconds — nothing should be evicted
        int evicted = store.evictExpired(10_000);
        assertEquals(0, evicted);
        assertEquals(1, store.size());
    }

    @Test
    void shouldEstimateMemory() {
        store.getOrCreateBucket("k1", 10);
        store.getOrCreateBucket("k2", 10);
        store.getOrCreateWindow("k3");

        long estimate = store.estimateMemoryBytes();
        assertEquals(3 * 200L, estimate); // 200 bytes per entry estimate
    }
}
