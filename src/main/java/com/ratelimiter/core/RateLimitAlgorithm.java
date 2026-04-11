package com.ratelimiter.core;

/**
 * Supported rate limiting algorithms.
 * The strategy pattern allows adding new algorithms without
 * modifying existing code — just implement RateLimiterStrategy
 * and register the new enum value.
 */
public enum RateLimitAlgorithm {

    TOKEN_BUCKET,
    SLIDING_WINDOW
}
