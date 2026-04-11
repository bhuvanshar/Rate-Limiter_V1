package com.ratelimiter.config;

import com.ratelimiter.service.ThrottleLogService;
import com.ratelimiter.store.CounterStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration class that sets up scheduled maintenance tasks
 * for the rate limiter subsystem.
 *
 * Two periodic tasks:
 * 1. Counter eviction: removes idle entries from the in-memory store.
 * 2. Throttle log flush: batch-inserts queued rejection logs into MySQL.
 *
 * Both run on the Spring scheduler thread pool and are non-blocking.
 */
@Configuration
public class RateLimiterConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterConfig.class);

    private final CounterStore counterStore;
    private final ThrottleLogService throttleLogService;
    private final long counterMaxIdleMs;

    public RateLimiterConfig(
            CounterStore counterStore,
            ThrottleLogService throttleLogService,
            @Value("${rate-limiter.counter-max-idle-seconds:300}") int counterMaxIdleSeconds) {
        this.counterStore = counterStore;
        this.throttleLogService = throttleLogService;
        this.counterMaxIdleMs = counterMaxIdleSeconds * 1000L;
    }

    /**
     * Evicts counter entries that haven't been accessed for longer
     * than the configured max idle time. Runs every 60 seconds.
     */
    @Scheduled(fixedDelayString = "${rate-limiter.cleanup-interval-seconds:60}000")
    public void evictExpiredCounters() {
        try {
            int evicted = counterStore.evictExpired(counterMaxIdleMs);
            if (evicted > 0) {
                log.info("Counter cleanup: evicted {} entries, {} remaining",
                        evicted, counterStore.size());
            }
        } catch (Exception e) {
            log.error("Counter cleanup failed", e);
        }
    }

    /**
     * Flushes the async throttle log queue to MySQL. Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    public void flushThrottleLogs() {
        try {
            throttleLogService.flush();
        } catch (Exception e) {
            log.error("Throttle log flush failed", e);
        }
    }
}
