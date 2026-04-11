package com.ratelimiter.store;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe sliding window counter state.
 *
 * Uses the "two-window weighted average" approach:
 *   effectiveCount = currentWindowCount + previousWindowCount * overlapFraction
 *
 * where overlapFraction = (windowSize - elapsedInCurrentWindow) / windowSize
 *
 * This gives O(1) space per key (just two counters + a window start timestamp)
 * while providing much better accuracy than a plain fixed window.
 */
public class SlidingWindowState {

    private final AtomicLong currentCount;
    private final AtomicLong previousCount;
    private final AtomicLong windowStartMs;
    private final AtomicLong lastAccessTimeMs;

    public SlidingWindowState() {
        this.currentCount = new AtomicLong(0);
        this.previousCount = new AtomicLong(0);
        this.windowStartMs = new AtomicLong(System.currentTimeMillis());
        this.lastAccessTimeMs = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Attempts to record a request. Returns the result including whether the
     * request is within the limit.
     *
     * @param maxRequests    maximum allowed requests per window
     * @param windowSizeMs   window duration in milliseconds
     * @return result of the check
     */
    public WindowResult tryRecord(long maxRequests, long windowSizeMs) {
        long now = System.currentTimeMillis();
        lastAccessTimeMs.set(now);

        // Check if we need to rotate windows
        long windowStart = windowStartMs.get();
        long elapsed = now - windowStart;

        if (elapsed >= windowSizeMs) {
            // Time to rotate: current becomes previous, start new window
            // This CAS ensures only one thread rotates
            if (windowStartMs.compareAndSet(windowStart, now)) {
                previousCount.set(currentCount.get());
                currentCount.set(0);
            }
            // Re-read after potential rotation
            windowStart = windowStartMs.get();
            elapsed = now - windowStart;
        }

        // Calculate weighted count
        double overlapFraction = Math.max(0, (windowSizeMs - elapsed)) / (double) windowSizeMs;
        long prevCount = previousCount.get();
        long currCount = currentCount.get();
        double effectiveCount = currCount + prevCount * overlapFraction;

        if (effectiveCount >= maxRequests) {
            // Calculate retry-after: time until enough requests from previous window "expire"
            long retryMs = (long) Math.ceil((windowSizeMs - elapsed) *
                    ((effectiveCount - maxRequests + 1) / (prevCount * overlapFraction + 1)));
            retryMs = Math.max(retryMs, 1);
            long resetMs = windowStart + windowSizeMs;
            return new WindowResult(false, (long) effectiveCount, maxRequests - (long) effectiveCount, retryMs, resetMs);
        }

        // Within limit — increment and allow
        currentCount.incrementAndGet();
        long remaining = maxRequests - (long) effectiveCount - 1;
        long resetMs = windowStart + windowSizeMs;
        return new WindowResult(true, (long) effectiveCount + 1, Math.max(0, remaining), 0, resetMs);
    }

    public long getLastAccessTimeMs() {
        return lastAccessTimeMs.get();
    }

    public record WindowResult(
            boolean allowed,
            long currentCount,
            long remaining,
            long retryAfterMs,
            long resetAtEpochMs
    ) {}
}
