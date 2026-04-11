package com.ratelimiter.core;

/**
 * Immutable snapshot of the request attributes needed for rate limiting.
 * Extracted once in the filter and passed through the evaluation pipeline
 * to avoid repeated header/attribute lookups.
 */
public record RequestContext(
        String userId,      // null if unauthenticated
        String ipAddress,
        String httpMethod,
        String requestPath
) {

    public String apiEndpoint() {
        return httpMethod + ":" + requestPath;
    }

    public boolean hasUserId() {
        return userId != null && !userId.isBlank();
    }
}
