package com.ratelimiter.dto;

/**
 * Admin endpoint response showing runtime status of the rate limiter.
 */
public record RateLimitStatusDto(
        boolean enabled,
        int activeRules,
        long activeCounterEntries,
        long totalThrottledLast5Min,
        long memorySizeEstimateBytes
) {}
