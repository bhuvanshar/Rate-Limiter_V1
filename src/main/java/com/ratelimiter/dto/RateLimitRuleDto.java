package com.ratelimiter.dto;

import com.ratelimiter.core.RateLimitAlgorithm;
import com.ratelimiter.core.RateLimitScope;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RateLimitRuleDto(
        Long id,

        @NotBlank(message = "Rule name is required")
        String ruleName,

        @NotNull(message = "Scope is required")
        RateLimitScope scope,

        String apiPattern,

        @NotNull(message = "Algorithm is required")
        RateLimitAlgorithm algorithm,

        @Min(value = 1, message = "Max requests must be at least 1")
        int maxRequests,

        @Min(value = 1, message = "Window seconds must be at least 1")
        int windowSeconds,

        Integer burstCapacity,

        boolean enabled,

        int priority
) {}
