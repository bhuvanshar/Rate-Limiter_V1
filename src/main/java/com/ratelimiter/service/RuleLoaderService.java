package com.ratelimiter.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ratelimiter.core.RequestContext;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.repository.RateLimitRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Loads and caches rate limit rules from MySQL.
 *
 * Design decisions:
 * - Uses Caffeine LoadingCache with a single key ("all") for simplicity.
 *   All enabled rules are loaded together because the dataset is small
 *   (typically <100 rules) and we need to match by multiple dimensions.
 * - The cache TTL (default 60s) means rule changes take effect within
 *   a minute without any restart or explicit invalidation.
 * - The warm-up on @PostConstruct ensures the first API request never
 *   pays the MySQL latency.
 * - Rules are returned pre-sorted by priority (desc) so the service layer
 *   can iterate and short-circuit on the first match.
 */
@Service
public class RuleLoaderService {

    private static final Logger log = LoggerFactory.getLogger(RuleLoaderService.class);
    private static final String CACHE_KEY = "all_rules";

    private final RateLimitRuleRepository ruleRepository;
    private final LoadingCache<String, List<RateLimitRule>> ruleCache;

    public RuleLoaderService(
            RateLimitRuleRepository ruleRepository,
            @Value("${rate-limiter.rule-cache-ttl-seconds:60}") int cacheTtlSeconds) {
        this.ruleRepository = ruleRepository;
        this.ruleCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(1) // Only one entry: the full rule list
                .recordStats()
                .build(key -> loadRulesFromDb());
    }

    @PostConstruct
    void warmUp() {
        try {
            List<RateLimitRule> rules = ruleCache.get(CACHE_KEY);
            log.info("Rate limiter warmed up with {} active rules", rules != null ? rules.size() : 0);
        } catch (Exception e) {
            log.warn("Failed to warm up rule cache — first request will load from DB", e);
        }
    }

    /**
     * Returns all enabled rules that could apply to this request context,
     * ordered by priority (highest first).
     *
     * Filtering logic:
     * - Rules with a USER scope are skipped if the request has no userId.
     * - Rules with an API pattern are matched against the request's API endpoint.
     */
    public List<RateLimitRule> getMatchingRules(RequestContext context) {
        List<RateLimitRule> allRules = ruleCache.get(CACHE_KEY);
        if (allRules == null || allRules.isEmpty()) {
            return Collections.emptyList();
        }

        String apiEndpoint = context.apiEndpoint();

        return allRules.stream()
                .filter(rule -> {
                    // Skip user-scoped rules for anonymous requests
                    if (rule.getScope().requiresUserId() && !context.hasUserId()) {
                        return false;
                    }
                    // Check API pattern match
                    return rule.matchesApi(apiEndpoint);
                })
                .toList();
    }

    /** Force reload rules from DB (useful for admin endpoints). */
    public void invalidateCache() {
        ruleCache.invalidateAll();
        log.info("Rule cache invalidated");
    }

    private List<RateLimitRule> loadRulesFromDb() {
        List<RateLimitRule> rules = ruleRepository.findAllEnabledOrderByPriorityDesc();
        log.debug("Loaded {} enabled rate limit rules from database", rules.size());
        return rules;
    }
}
