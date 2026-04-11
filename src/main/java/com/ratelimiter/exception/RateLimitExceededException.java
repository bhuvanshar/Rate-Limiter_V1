package com.ratelimiter.exception;

import com.ratelimiter.core.ThrottleDecision;

/**
 * Thrown (or used as a signal) when a request exceeds its rate limit.
 * Carries the ThrottleDecision for building the 429 response.
 */
public class RateLimitExceededException extends RuntimeException {

    private final ThrottleDecision decision;

    public RateLimitExceededException(ThrottleDecision decision) {
        super("Rate limit exceeded for key: " + decision.ruleKey());
        this.decision = decision;
    }

    public ThrottleDecision getDecision() {
        return decision;
    }
}
