package com.ratelimiter.core;

import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.TokenBucketState;
import org.springframework.stereotype.Component;

/**
 * Token Bucket algorithm implementation.
 *
 * How it works:
 * 1. Each key has a bucket with a maximum capacity (burstCapacity).
 * 2. Tokens are added at a steady rate (maxRequests / windowSeconds).
 * 3. Each request consumes one token.
 * 4. If the bucket is empty, the request is rejected.
 * 5. Tokens accumulate up to capacity, naturally allowing bursts.
 *
 * The refill is "lazy" — computed at check time based on elapsed time
 * since last refill. No background thread needed.
 *
 * Example: rule says 100 req/min, burst capacity 100.
 *   - Refill rate: 100/60000 = 0.001667 tokens/ms
 *   - A client idle for 30s has ~50 tokens to burst with.
 *   - A client sending steadily gets ~1.67 tokens/second.
 */
@Component
public class TokenBucketRateLimiter implements RateLimiterStrategy {

    private final CounterStore counterStore;

    public TokenBucketRateLimiter(CounterStore counterStore) {
        this.counterStore = counterStore;
    }

    @Override
    public RateLimitAlgorithm algorithm() {
        return RateLimitAlgorithm.TOKEN_BUCKET;
    }

    @Override
    public ThrottleDecision tryConsume(String key, RateLimitRule rule) {
        long capacity = rule.effectiveBurstCapacity();
        double refillRate = rule.refillRatePerMs();

        TokenBucketState bucket = counterStore.getOrCreateBucket(key, capacity);
        TokenBucketState.ConsumeResult result = bucket.tryConsume(capacity, refillRate);

        if (result.consumed()) {
            return ThrottleDecision.allowed(
                    key,
                    capacity,
                    result.remainingTokens(),
                    result.resetAtEpochMs(),
                    rule.getRuleName()
            );
        } else {
            return ThrottleDecision.rejected(
                    key,
                    capacity,
                    result.retryAfterMs(),
                    result.resetAtEpochMs(),
                    rule.getRuleName()
            );
        }
    }
}
