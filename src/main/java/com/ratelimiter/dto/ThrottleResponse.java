package com.ratelimiter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * JSON response body returned when a request is rate-limited (HTTP 429).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThrottleResponse(
        int status,
        String error,
        String message,
        long retryAfterMs,
        Instant retryAfter,
        String rateLimitKey,
        Long limit,
        Long remaining
) {

    public static ThrottleResponse of(String message, long retryAfterMs, String key,
                                       long limit, long remaining) {
        Instant retryAt = retryAfterMs > 0
                ? Instant.now().plusMillis(retryAfterMs)
                : null;
        return new ThrottleResponse(
                429,
                "Too Many Requests",
                message,
                retryAfterMs,
                retryAt,
                key,
                limit,
                remaining
        );
    }
}
