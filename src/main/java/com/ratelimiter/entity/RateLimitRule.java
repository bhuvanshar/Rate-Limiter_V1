package com.ratelimiter.entity;

import com.ratelimiter.core.RateLimitAlgorithm;
import com.ratelimiter.core.RateLimitScope;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rate_limit_rule", indexes = {
        @Index(name = "idx_scope_enabled", columnList = "scope, enabled"),
        @Index(name = "idx_api_pattern", columnList = "apiPattern")
})
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RateLimitScope scope;

    /** API pattern for matching, e.g. "GET:/api/orders" or "POST:/api/*". Null for non-API scopes. */
    @Column(length = 256)
    private String apiPattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RateLimitAlgorithm algorithm;

    /** Maximum requests allowed in the window (or bucket capacity for token bucket). */
    @Column(nullable = false)
    private int maxRequests;

    /** Window duration in seconds (or refill period for token bucket). */
    @Column(nullable = false)
    private int windowSeconds;

    /** For token bucket: max burst capacity. Defaults to maxRequests if null. */
    private Integer burstCapacity;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Higher priority rules are evaluated first. */
    @Column(nullable = false)
    private int priority = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- Derived accessors ---

    /** Effective burst capacity: uses burstCapacity if set, otherwise maxRequests. */
    public int effectiveBurstCapacity() {
        return burstCapacity != null ? burstCapacity : maxRequests;
    }

    /** Refill rate in tokens per millisecond for the token bucket algorithm. */
    public double refillRatePerMs() {
        return (double) maxRequests / (windowSeconds * 1000.0);
    }

    /**
     * Returns true if this rule's apiPattern matches the given API endpoint.
     * Supports exact match and simple wildcard (*) at the end.
     */
    public boolean matchesApi(String apiEndpoint) {
        if (apiPattern == null || apiPattern.isBlank()) {
            return true; // No pattern means matches all APIs
        }
        if (apiPattern.endsWith("*")) {
            String prefix = apiPattern.substring(0, apiPattern.length() - 1);
            return apiEndpoint.startsWith(prefix);
        }
        return apiPattern.equals(apiEndpoint);
    }

    // --- Standard getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public RateLimitScope getScope() { return scope; }
    public void setScope(RateLimitScope scope) { this.scope = scope; }

    public String getApiPattern() { return apiPattern; }
    public void setApiPattern(String apiPattern) { this.apiPattern = apiPattern; }

    public RateLimitAlgorithm getAlgorithm() { return algorithm; }
    public void setAlgorithm(RateLimitAlgorithm algorithm) { this.algorithm = algorithm; }

    public int getMaxRequests() { return maxRequests; }
    public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }

    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }

    public Integer getBurstCapacity() { return burstCapacity; }
    public void setBurstCapacity(Integer burstCapacity) { this.burstCapacity = burstCapacity; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
