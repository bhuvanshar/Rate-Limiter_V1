package com.ratelimiter.store;

/**
 * Abstraction for rate limit state storage.
 *
 * The in-memory implementation uses ConcurrentHashMap + atomics.
 * A future Redis implementation would use Redis commands (MULTI/EXEC or Lua scripts).
 *
 * This interface is intentionally low-level — it provides raw state access,
 * and the rate limiting algorithms (strategies) build their logic on top.
 */
public interface CounterStore {

    /**
     * Gets or creates a TokenBucketState for the given key.
     * Implementations must be thread-safe.
     *
     * @param key      the composite rate limit key
     * @param capacity initial bucket capacity (used only on creation)
     * @return the token bucket state for this key
     */
    TokenBucketState getOrCreateBucket(String key, long capacity);

    /**
     * Gets or creates a SlidingWindowState for the given key.
     *
     * @param key the composite rate limit key
     * @return the sliding window state for this key
     */
    SlidingWindowState getOrCreateWindow(String key);

    /**
     * Removes all entries that haven't been accessed for longer
     * than maxIdleMs. Returns the number of entries evicted.
     */
    int evictExpired(long maxIdleMs);

    /**
     * Returns the number of active entries (for monitoring).
     */
    long size();

    /**
     * Estimates memory usage in bytes (for monitoring).
     */
    long estimateMemoryBytes();
}
