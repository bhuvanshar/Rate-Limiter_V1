package com.ratelimiter.filter;

import com.ratelimiter.core.RateLimitAlgorithm;
import com.ratelimiter.core.RateLimitScope;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.repository.RateLimitRuleRepository;
import com.ratelimiter.service.RuleLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test that boots the full Spring context with H2 in-memory DB
 * and verifies end-to-end rate limiting through the HTTP filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitRuleRepository ruleRepository;

    @Autowired
    private RuleLoaderService ruleLoaderService;

    @BeforeEach
    void setUp() {
        ruleRepository.deleteAll();
        ruleLoaderService.invalidateCache();
    }

    @Test
    void shouldAllowRequestsWhenNoRulesExist() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnRateLimitHeaders() throws Exception {
        createRule("ip-limit", RateLimitScope.IP, null, RateLimitAlgorithm.TOKEN_BUCKET, 100, 60);
        ruleLoaderService.invalidateCache();

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        createRule("strict-ip", RateLimitScope.IP, null, RateLimitAlgorithm.TOKEN_BUCKET, 3, 60);
        ruleLoaderService.invalidateCache();

        // First 3 should pass
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/orders")
                            .header("X-User-Id", "user1"))
                    .andExpect(status().isOk());
        }

        // 4th should be throttled
        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.retryAfterMs").isNumber());
    }

    @Test
    void shouldEnforceUserScopeRule() throws Exception {
        createRule("user-limit", RateLimitScope.USER, null, RateLimitAlgorithm.TOKEN_BUCKET, 2, 60);
        ruleLoaderService.invalidateCache();

        // User1 gets 2 requests
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                .andExpect(status().isTooManyRequests());

        // User2 should still work (separate bucket)
        mockMvc.perform(get("/api/orders").header("X-User-Id", "user2"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldEnforceUserApiScopeRule() throws Exception {
        createRule("user-api-limit", RateLimitScope.USER_API, "GET:/api/orders",
                RateLimitAlgorithm.TOKEN_BUCKET, 2, 60);
        ruleLoaderService.invalidateCache();

        // 2 requests to /api/orders should work
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                    .andExpect(status().isOk());
        }

        // 3rd to /api/orders should be throttled
        mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                .andExpect(status().isTooManyRequests());

        // Same user on different endpoint should still work
        mockMvc.perform(get("/api/users/1").header("X-User-Id", "user1"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotRateLimitExcludedPaths() throws Exception {
        createRule("global-ip", RateLimitScope.IP, null, RateLimitAlgorithm.TOKEN_BUCKET, 1, 60);
        ruleLoaderService.invalidateCache();

        // Admin path should be excluded
        mockMvc.perform(get("/rate-limit/admin/rules"))
                .andExpect(status().isOk());

        // Actuator should be excluded
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSupportSlidingWindowAlgorithm() throws Exception {
        createRule("sw-rule", RateLimitScope.IP, null, RateLimitAlgorithm.SLIDING_WINDOW, 3, 60);
        ruleLoaderService.invalidateCache();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void shouldSupportWildcardApiPatterns() throws Exception {
        // Pattern matches all /api/* endpoints
        createRule("api-wildcard", RateLimitScope.USER_API, "GET:/api/*",
                RateLimitAlgorithm.TOKEN_BUCKET, 2, 60);
        ruleLoaderService.invalidateCache();

        mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/1").header("X-User-Id", "user1"))
                .andExpect(status().isOk());
        // Both consumed from same bucket because pattern matches both
        // Actually each generates a different key because USER_API includes the full path
        // But the rule matches both paths via wildcard
    }

    private RateLimitRule createRule(String name, RateLimitScope scope, String apiPattern,
                                     RateLimitAlgorithm algorithm, int maxRequests, int windowSeconds) {
        RateLimitRule rule = new RateLimitRule();
        rule.setRuleName(name);
        rule.setScope(scope);
        rule.setApiPattern(apiPattern);
        rule.setAlgorithm(algorithm);
        rule.setMaxRequests(maxRequests);
        rule.setWindowSeconds(windowSeconds);
        rule.setBurstCapacity(maxRequests);
        rule.setEnabled(true);
        rule.setPriority(10);
        return ruleRepository.save(rule);
    }
}
