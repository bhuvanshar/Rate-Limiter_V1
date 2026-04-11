package com.ratelimiter.controller;

import com.ratelimiter.core.RateLimitAlgorithm;
import com.ratelimiter.core.RateLimitScope;
import com.ratelimiter.dto.RateLimitRuleDto;
import com.ratelimiter.dto.RateLimitStatusDto;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.repository.RateLimitRuleRepository;
import com.ratelimiter.repository.ThrottleLogRepository;
import com.ratelimiter.service.RuleLoaderService;
import com.ratelimiter.store.CounterStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Admin endpoints for managing rate limit rules and monitoring.
 * In production, these should be secured with appropriate authentication.
 */
@RestController
@RequestMapping("/rate-limit/admin")
public class RateLimitAdminController {

    private final RateLimitRuleRepository ruleRepository;
    private final ThrottleLogRepository throttleLogRepository;
    private final RuleLoaderService ruleLoaderService;
    private final CounterStore counterStore;

    public RateLimitAdminController(
            RateLimitRuleRepository ruleRepository,
            ThrottleLogRepository throttleLogRepository,
            RuleLoaderService ruleLoaderService,
            CounterStore counterStore) {
        this.ruleRepository = ruleRepository;
        this.throttleLogRepository = throttleLogRepository;
        this.ruleLoaderService = ruleLoaderService;
        this.counterStore = counterStore;
    }

    @GetMapping("/rules")
    public ResponseEntity<List<RateLimitRule>> listRules() {
        return ResponseEntity.ok(ruleRepository.findAll());
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<RateLimitRule> getRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
    }

    @PostMapping("/rules")
    public ResponseEntity<RateLimitRule> createRule(@Valid @RequestBody RateLimitRuleDto dto) {
        RateLimitRule rule = mapToEntity(new RateLimitRule(), dto);
        RateLimitRule saved = ruleRepository.save(rule);
        ruleLoaderService.invalidateCache();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<RateLimitRule> updateRule(@PathVariable Long id,
                                                      @Valid @RequestBody RateLimitRuleDto dto) {
        RateLimitRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        mapToEntity(existing, dto);
        RateLimitRule saved = ruleRepository.save(existing);
        ruleLoaderService.invalidateCache();
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
        }
        ruleRepository.deleteById(id);
        ruleLoaderService.invalidateCache();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<String> invalidateCache() {
        ruleLoaderService.invalidateCache();
        return ResponseEntity.ok("Cache invalidated");
    }

    @GetMapping("/status")
    public ResponseEntity<RateLimitStatusDto> getStatus() {
        long throttledLast5Min = throttleLogRepository.countByRejectedAtAfter(
                Instant.now().minus(5, ChronoUnit.MINUTES));

        int activeRules = ruleRepository.findAllEnabledOrderByPriorityDesc().size();

        RateLimitStatusDto status = new RateLimitStatusDto(
                true,
                activeRules,
                counterStore.size(),
                throttledLast5Min,
                counterStore.estimateMemoryBytes()
        );
        return ResponseEntity.ok(status);
    }

    private RateLimitRule mapToEntity(RateLimitRule entity, RateLimitRuleDto dto) {
        entity.setRuleName(dto.ruleName());
        entity.setScope(dto.scope());
        entity.setApiPattern(dto.apiPattern());
        entity.setAlgorithm(dto.algorithm());
        entity.setMaxRequests(dto.maxRequests());
        entity.setWindowSeconds(dto.windowSeconds());
        entity.setBurstCapacity(dto.burstCapacity());
        entity.setEnabled(dto.enabled());
        entity.setPriority(dto.priority());
        return entity;
    }
}
