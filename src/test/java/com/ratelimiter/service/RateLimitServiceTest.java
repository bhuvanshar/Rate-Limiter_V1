package com.ratelimiter.service;

import com.ratelimiter.core.*;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.InMemoryCounterStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RuleLoaderService ruleLoader;

    @Mock
    private ThrottleLogService throttleLogService;

    private RateLimitService rateLimitService;
    private InMemoryCounterStore counterStore;

    @BeforeEach
    void setUp() {
        counterStore = new InMemoryCounterStore();
        TokenBucketRateLimiter tokenBucket = new TokenBucketRateLimiter(counterStore);
        SlidingWindowRateLimiter slidingWindow = new SlidingWindowRateLimiter(counterStore);
        StrategyRegistry registry = new StrategyRegistry(List.of(tokenBucket, slidingWindow));
        KeyResolver keyResolver = new KeyResolver();

        rateLimitService = new RateLimitService(
                ruleLoader,
                keyResolver,
                registry,
                throttleLogService,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldAllowWhenNoRulesMatch() {
        when(ruleLoader.getMatchingRules(any())).thenReturn(Collections.emptyList());

        RequestContext ctx = new RequestContext("user1", "10.0.0.1", "GET", "/api/orders");
        ThrottleDecision decision = rateLimitService.evaluate(ctx);

        assertTrue(decision.allowed());
    }

    @Test
    void shouldEnforceTokenBucketRule() {
        RateLimitRule rule = createRule("test", RateLimitScope.USER, RateLimitAlgorithm.TOKEN_BUCKET, 5, 60);
        when(ruleLoader.getMatchingRules(any())).thenReturn(List.of(rule));

        RequestContext ctx = new RequestContext("user1", "10.0.0.1", "GET", "/api/orders");

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.evaluate(ctx).allowed());
        }

        ThrottleDecision decision = rateLimitService.evaluate(ctx);
        assertFalse(decision.allowed());
        verify(throttleLogService, times(1)).logThrottle(any(), any(), any());
    }

    @Test
    void shouldSkipUserRuleForAnonymousRequest() {
        RateLimitRule userRule = createRule("user-rule", RateLimitScope.USER, RateLimitAlgorithm.TOKEN_BUCKET, 5, 60);
        when(ruleLoader.getMatchingRules(any())).thenReturn(List.of(userRule));

        // Anonymous request (no userId)
        RequestContext ctx = new RequestContext(null, "10.0.0.1", "GET", "/api/orders");
        ThrottleDecision decision = rateLimitService.evaluate(ctx);

        // Should be allowed because the USER rule can't apply without userId
        assertTrue(decision.allowed());
    }

    @Test
    void shouldEnforceMultipleRules() {
        // User rule: 10 req/min
        RateLimitRule userRule = createRule("user-global", RateLimitScope.USER, RateLimitAlgorithm.TOKEN_BUCKET, 10, 60);
        // User+API rule: 3 req/min (tighter)
        RateLimitRule userApiRule = createRule("user-api", RateLimitScope.USER_API, RateLimitAlgorithm.TOKEN_BUCKET, 3, 60);

        when(ruleLoader.getMatchingRules(any())).thenReturn(List.of(userApiRule, userRule));

        RequestContext ctx = new RequestContext("user1", "10.0.0.1", "GET", "/api/orders");

        // First 3 should pass (limited by user+api rule)
        for (int i = 0; i < 3; i++) {
            assertTrue(rateLimitService.evaluate(ctx).allowed());
        }

        // 4th should fail due to user+api rule
        assertFalse(rateLimitService.evaluate(ctx).allowed());
    }

    @Test
    void shouldReturnTightestAllowedDecision() {
        // Loose rule: 100 req/min
        RateLimitRule looseRule = createRule("loose", RateLimitScope.USER, RateLimitAlgorithm.TOKEN_BUCKET, 100, 60);
        // Tight rule: 50 req/min
        RateLimitRule tightRule = createRule("tight", RateLimitScope.IP, RateLimitAlgorithm.TOKEN_BUCKET, 50, 60);

        when(ruleLoader.getMatchingRules(any())).thenReturn(List.of(tightRule, looseRule));

        RequestContext ctx = new RequestContext("user1", "10.0.0.1", "GET", "/api/orders");
        ThrottleDecision decision = rateLimitService.evaluate(ctx);

        assertTrue(decision.allowed());
        // The remaining should reflect the tighter rule (49 remaining after 1 request from 50)
        assertEquals(49, decision.remaining());
    }

    private RateLimitRule createRule(String name, RateLimitScope scope, RateLimitAlgorithm algo,
                                     int maxRequests, int windowSeconds) {
        RateLimitRule rule = new RateLimitRule();
        rule.setId(1L);
        rule.setRuleName(name);
        rule.setScope(scope);
        rule.setAlgorithm(algo);
        rule.setMaxRequests(maxRequests);
        rule.setWindowSeconds(windowSeconds);
        rule.setBurstCapacity(maxRequests);
        rule.setEnabled(true);
        return rule;
    }
}
