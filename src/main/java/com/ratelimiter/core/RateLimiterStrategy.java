package com.ratelimiter.core;

import com.ratelimiter.entity.RateLimitRule;

/**
 * Strategy interface for rate limiting algorithms.
 *
 * Each implementation encapsulates a specific algorithm (token bucket,
 * sliding window, etc.) and operates against a CounterStore for state
 * management. This design allows adding new algorithms by:
 *   1. Implementing this interface
 *   2. Adding the enum value to RateLimitAlgorithm
 *   3. Registering in StrategyRegistry
 *
 * No existing code needs to change.
 */
public interface RateLimiterStrategy {

    /**
     * Returns the algorithm this strategy implements.
     */
    RateLimitAlgorithm algorithm();

    /**
     * Attempts to consume one request token for the given key under the given rule.
     *
     * @param key  the resolved composite rate limit key
     * @param rule the rate limit rule defining capacity and window
     * @return a ThrottleDecision indicating whether the request is allowed
     */
    ThrottleDecision tryConsume(String key, RateLimitRule rule);
}
