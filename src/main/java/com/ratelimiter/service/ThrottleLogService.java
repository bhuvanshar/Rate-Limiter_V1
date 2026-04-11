package com.ratelimiter.service;

import com.ratelimiter.core.RequestContext;
import com.ratelimiter.core.ThrottleDecision;
import com.ratelimiter.entity.ThrottleLog;
import com.ratelimiter.repository.ThrottleLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Asynchronous throttle event logger.
 *
 * Design:
 * - Rejected requests are queued into a bounded LinkedBlockingQueue.
 * - A scheduled flush drains the queue and batch-inserts into MySQL.
 * - The queue has a configurable max capacity (default 10K) to prevent
 *   OOM under sustained attack. If the queue is full, events are dropped
 *   (logged as a warning) — this is intentional: we prefer dropping audit
 *   logs over blocking the hot path.
 *
 * The @Async annotation ensures the log call returns immediately and
 * doesn't block the filter response.
 */
@Service
public class ThrottleLogService {

    private static final Logger log = LoggerFactory.getLogger(ThrottleLogService.class);

    private final ThrottleLogRepository logRepository;
    private final LinkedBlockingQueue<ThrottleLog> logQueue;
    private final int batchSize;

    public ThrottleLogService(
            ThrottleLogRepository logRepository,
            @Value("${rate-limiter.throttle-log-queue-capacity:10000}") int queueCapacity,
            @Value("${rate-limiter.throttle-log-batch-size:100}") int batchSize) {
        this.logRepository = logRepository;
        this.logQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
    }

    /**
     * Enqueues a throttle event for async persistence.
     * Non-blocking: if the queue is full, the event is dropped.
     */
    @Async
    public void logThrottle(ThrottleDecision decision, RequestContext context, Long ruleId) {
        ThrottleLog entry = ThrottleLog.create()
                .withRuleId(ruleId)
                .withRuleName(decision.ruleName())
                .withThrottleKey(decision.ruleKey())
                .withUserId(context.userId())
                .withIpAddress(context.ipAddress())
                .withApiEndpoint(context.apiEndpoint())
                .withRejectedAt(Instant.now())
                .withTokensRemaining((double) decision.remaining());

        if (!logQueue.offer(entry)) {
            log.warn("Throttle log queue full — dropping event for key: {}", decision.ruleKey());
        }
    }

    /**
     * Flushes the queue to MySQL in batches. Called by the scheduled task.
     */
    public int flush() {
        if (logQueue.isEmpty()) return 0;

        List<ThrottleLog> batch = new ArrayList<>(batchSize);
        int total = 0;

        while (!logQueue.isEmpty()) {
            batch.clear();
            logQueue.drainTo(batch, batchSize);
            if (!batch.isEmpty()) {
                try {
                    logRepository.saveAll(batch);
                    total += batch.size();
                } catch (Exception e) {
                    log.error("Failed to flush {} throttle log entries", batch.size(), e);
                }
            }
        }

        if (total > 0) {
            log.debug("Flushed {} throttle log entries to database", total);
        }
        return total;
    }
}
