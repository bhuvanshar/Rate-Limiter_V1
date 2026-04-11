package com.ratelimiter.core;

/**
 * The result of evaluating a single rate limit rule against a request.
 * Contains everything needed to build response headers and audit logs.
 */
public record ThrottleDecision(
        boolean allowed,
        String ruleKey,         // the composite key that was evaluated
        long limit,             // the max requests configured
        long remaining,         // tokens/requests remaining
        long retryAfterMs,      // ms until the client should retry (0 if allowed)
        long resetAtEpochMs,    // epoch ms when the window/bucket resets
        String ruleName         // human-readable rule name for logging
) {

    public static ThrottleDecision allowed(String ruleKey, long limit, long remaining,
                                            long resetAtEpochMs, String ruleName) {
        return new ThrottleDecision(true, ruleKey, limit, remaining, 0, resetAtEpochMs, ruleName);
    }

    public static ThrottleDecision rejected(String ruleKey, long limit, long retryAfterMs,
                                             long resetAtEpochMs, String ruleName) {
        return new ThrottleDecision(false, ruleKey, limit, 0, retryAfterMs, resetAtEpochMs, ruleName);
    }
}
