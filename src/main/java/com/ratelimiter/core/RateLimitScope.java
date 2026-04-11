package com.ratelimiter.core;

/**
 * Defines the dimensions used to identify a rate limit target.
 * Each scope determines which combination of request attributes
 * forms the throttle key.
 */
public enum RateLimitScope {

    USER,           // userId only
    IP,             // IP address only
    API,            // HTTP method + path only
    USER_API,       // userId + API endpoint
    IP_API,         // IP + API endpoint
    USER_IP,        // userId + IP
    USER_IP_API;    // userId + IP + API (most granular)

    /**
     * Returns true if this scope requires a userId to build the key.
     */
    public boolean requiresUserId() {
        return this == USER || this == USER_API || this == USER_IP || this == USER_IP_API;
    }

    /**
     * Returns true if this scope requires an IP address to build the key.
     */
    public boolean requiresIp() {
        return this == IP || this == IP_API || this == USER_IP || this == USER_IP_API;
    }

    /**
     * Returns true if this scope requires the API endpoint to build the key.
     */
    public boolean requiresApi() {
        return this == API || this == USER_API || this == IP_API || this == USER_IP_API;
    }
}
