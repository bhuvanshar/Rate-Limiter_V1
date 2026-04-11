package com.ratelimiter.core;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that maps algorithm enum values to their strategy implementations.
 *
 * Spring auto-discovers all RateLimiterStrategy beans. This registry
 * indexes them by algorithm so the service layer can look up the correct
 * strategy in O(1) time without if-else chains.
 *
 * To add a new algorithm:
 * 1. Add enum value to RateLimitAlgorithm
 * 2. Implement RateLimiterStrategy
 * 3. Annotate with @Component
 * That's it — Spring injects it here automatically.
 */
@Component
public class StrategyRegistry {

    private final Map<RateLimitAlgorithm, RateLimiterStrategy> strategies;

    public StrategyRegistry(List<RateLimiterStrategy> strategyList) {
        this.strategies = new EnumMap<>(RateLimitAlgorithm.class);
        for (RateLimiterStrategy strategy : strategyList) {
            strategies.put(strategy.algorithm(), strategy);
        }
    }

    public RateLimiterStrategy getStrategy(RateLimitAlgorithm algorithm) {
        RateLimiterStrategy strategy = strategies.get(algorithm);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy registered for algorithm: " + algorithm);
        }
        return strategy;
    }
}
