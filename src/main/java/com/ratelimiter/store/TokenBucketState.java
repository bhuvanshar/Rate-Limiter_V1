package com.ratelimiter.store;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe token bucket state using atomic operations.
 *
 * The trick for lock-free token bucket: we store tokens as a fixed-point
 * long (tokens * 1_000_000) to avoid floating-point atomics. The last
 * refill timestamp is stored as epoch milliseconds.
 *
 * All operations use CAS loops which are wait-free on modern x86/ARM.
 */
public class TokenBucketState {

    private static final long PRECISION = 1_000_000L; // microtokens per token

    private final AtomicLong microTokens;
    private final AtomicLong lastRefillTimeMs;
    private final AtomicLong lastAccessTimeMs;

    public TokenBucketState(long capacity) {
        this.microTokens = new AtomicLong(capacity * PRECISION);
        long now = System.currentTimeMillis();
        this.lastRefillTimeMs = new AtomicLong(now);
        this.lastAccessTimeMs = new AtomicLong(now);
    }

    /**
     * Attempts to consume one token, lazily refilling based on elapsed time.
     *
     * @param capacity      max bucket capacity
     * @param refillRatePerMs tokens added per millisecond
     * @return remaining tokens after this operation, or -1 if bucket was empty
     */
    public ConsumeResult tryConsume(long capacity, double refillRatePerMs) {
        long now = System.currentTimeMillis();
        lastAccessTimeMs.set(now);

        // CAS loop for atomic refill + consume
        while (true) {
            long currentMicro = microTokens.get();
            long lastRefill = lastRefillTimeMs.get();
            long elapsedMs = now - lastRefill;

            // Calculate refill
            long refillMicro = (long) (elapsedMs * refillRatePerMs * PRECISION);
            long maxMicro = capacity * PRECISION;
            long newMicro = Math.min(currentMicro + refillMicro, maxMicro);

            // Try to consume one token
            long afterConsume = newMicro - PRECISION;

            if (afterConsume < 0) {
                // Bucket is empty — calculate when next token arrives
                long deficit = PRECISION - newMicro; // microtokens needed
                long waitMs = (long) Math.ceil((double) deficit / (refillRatePerMs * PRECISION));
                long remaining = 0;
                long resetMs = now + (long) Math.ceil((double)(maxMicro - newMicro) / (refillRatePerMs * PRECISION));
                return new ConsumeResult(false, remaining, waitMs, resetMs);
            }

            // Attempt CAS
            if (microTokens.compareAndSet(currentMicro, afterConsume)) {
                lastRefillTimeMs.compareAndSet(lastRefill, now);
                long remaining = afterConsume / PRECISION;
                long resetMs = now + (long) Math.ceil((double)(maxMicro - afterConsume) / (refillRatePerMs * PRECISION));
                return new ConsumeResult(true, remaining, 0, resetMs);
            }
            // CAS failed — another thread modified the state, retry
        }
    }

    public long getLastAccessTimeMs() {
        return lastAccessTimeMs.get();
    }

    /**
     * Result of a consume attempt. Immutable.
     */
    public record ConsumeResult(
            boolean consumed,
            long remainingTokens,
            long retryAfterMs,
            long resetAtEpochMs
    ) {}
}
