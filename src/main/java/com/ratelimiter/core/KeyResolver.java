package com.ratelimiter.core;

import org.springframework.stereotype.Component;

/**
 * Builds deterministic, ordered composite keys from request context
 * and rule scope. The key format is designed to be:
 *   - Human-readable for debugging
 *   - Deterministic (same inputs always produce same key)
 *   - Compact (uses short prefixes)
 *
 * Examples:
 *   USER scope:          "user:42"
 *   IP scope:            "ip:10.0.0.1"
 *   USER_API scope:      "user:42|api:GET:/api/orders"
 *   USER_IP_API scope:   "user:42|ip:10.0.0.1|api:POST:/api/checkout"
 */
@Component
public class KeyResolver {

    private static final char SEPARATOR = '|';

    /**
     * Resolves a composite key for the given scope and request context.
     * Returns null if the scope requires a dimension that isn't present
     * in the context (e.g., USER scope but userId is null).
     */
    public String resolve(RateLimitScope scope, RequestContext context) {
        if (scope.requiresUserId() && !context.hasUserId()) {
            return null; // Cannot evaluate user-based rules for anonymous requests
        }

        StringBuilder sb = new StringBuilder(64);

        if (scope.requiresUserId()) {
            sb.append("user:").append(context.userId());
        }

        if (scope.requiresIp()) {
            if (!sb.isEmpty()) sb.append(SEPARATOR);
            sb.append("ip:").append(context.ipAddress());
        }

        if (scope.requiresApi()) {
            if (!sb.isEmpty()) sb.append(SEPARATOR);
            sb.append("api:").append(context.apiEndpoint());
        }

        return sb.toString();
    }
}
