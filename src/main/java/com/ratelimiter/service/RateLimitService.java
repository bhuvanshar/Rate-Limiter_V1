package com.ratelimiter.service;

import com.ratelimiter.core.*;
import com.ratelimiter.entity.RateLimitRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The main orchestrator for rate limit evaluation.
 *
 * Request flow:
 * 1. Load matching rules for the request context.
 * 2. For each rule (priority order):
 *    a. Resolve the composite key.
 *    b. Look up the strategy for the rule's algorithm.
 *    c. Attempt to consume a token.
 *    d. If rejected, return immediately with the rejection decision.
 * 3. If all rules pass, return the decision from the tightest rule
 *    (lowest remaining tokens) for accurate response headers.
 *
 * All rules are evaluated independently. A request must satisfy ALL
 * matching rules to be allowed. This gives operators the ability to
 * compose orthogonal limits (e.g., 100/min per user AND 20/min per
 * user+API).
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RuleLoaderService ruleLoader;
    private final KeyResolver keyResolver;
    private final StrategyRegistry strategyRegistry;
    private final ThrottleLogService throttleLogService;

    // Metrics
    private final Counter allowedCounter;
    private final Counter rejectedCounter;
    private final Timer evaluationTimer;

    public RateLimitService(
            RuleLoaderService ruleLoader,
            KeyResolver keyResolver,
            StrategyRegistry strategyRegistry,
            ThrottleLogService throttleLogService,
            MeterRegistry meterRegistry) {
        this.ruleLoader = ruleLoader;
        this.keyResolver = keyResolver;
        this.strategyRegistry = strategyRegistry;
        this.throttleLogService = throttleLogService;

        this.allowedCounter = Counter.builder("rate_limit.decisions")
                .tag("result", "allowed")
                .description("Number of allowed requests")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("rate_limit.decisions")
                .tag("result", "rejected")
                .description("Number of rejected requests")
                .register(meterRegistry);
        this.evaluationTimer = Timer.builder("rate_limit.evaluation_time")
                .description("Time spent evaluating rate limits")
                .register(meterRegistry);
    }

    /**
     * Evaluates all matching rate limit rules for the given request.
     * Returns the most restrictive decision.
     */
    public ThrottleDecision evaluate(RequestContext context) {
        return evaluationTimer.record(() -> doEvaluate(context));
    }

    private ThrottleDecision doEvaluate(RequestContext context) {
        List<RateLimitRule> rules = ruleLoader.getMatchingRules(context);

        if (rules.isEmpty()) {
            allowedCounter.increment();
            return ThrottleDecision.allowed("none", 0, 0, 0, "no-rule");
        }

        ThrottleDecision tightest = null;

        for (RateLimitRule rule : rules) {
            String key = keyResolver.resolve(rule.getScope(), context);
            if (key == null) {
                continue; // Scope requires a dimension not present in context
            }

            RateLimiterStrategy strategy = strategyRegistry.getStrategy(rule.getAlgorithm());
            ThrottleDecision decision = strategy.tryConsume(key, rule);

            if (!decision.allowed()) {
                // Rejected — log and return immediately
                rejectedCounter.increment();
                log.info("Request throttled: key={}, rule={}, retryAfter={}ms",
                        key, rule.getRuleName(), decision.retryAfterMs());
                throttleLogService.logThrottle(decision, context, rule.getId());
                return decision;
            }

            // Track the tightest (fewest remaining) allowed decision for response headers
            if (tightest == null || decision.remaining() < tightest.remaining()) {
                tightest = decision;
            }
        }

        allowedCounter.increment();
        return tightest != null ? tightest : ThrottleDecision.allowed("none", 0, 0, 0, "no-rule");
    }
}
