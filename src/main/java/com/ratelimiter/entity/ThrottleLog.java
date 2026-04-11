package com.ratelimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "throttle_log", indexes = {
        @Index(name = "idx_rejected_at", columnList = "rejectedAt"),
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_throttle_key", columnList = "throttleKey")
})
public class ThrottleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ruleId;

    @Column(length = 128)
    private String ruleName;

    @Column(nullable = false, length = 512)
    private String throttleKey;

    @Column(length = 128)
    private String userId;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 256)
    private String apiEndpoint;

    @Column(nullable = false)
    private Instant rejectedAt;

    private Double tokensRemaining;

    @PrePersist
    protected void onCreate() {
        if (rejectedAt == null) {
            rejectedAt = Instant.now();
        }
    }

    // --- Builder-style setters for clean construction ---

    public static ThrottleLog create() {
        return new ThrottleLog();
    }

    public ThrottleLog withRuleId(Long ruleId) { this.ruleId = ruleId; return this; }
    public ThrottleLog withRuleName(String ruleName) { this.ruleName = ruleName; return this; }
    public ThrottleLog withThrottleKey(String key) { this.throttleKey = key; return this; }
    public ThrottleLog withUserId(String userId) { this.userId = userId; return this; }
    public ThrottleLog withIpAddress(String ip) { this.ipAddress = ip; return this; }
    public ThrottleLog withApiEndpoint(String api) { this.apiEndpoint = api; return this; }
    public ThrottleLog withRejectedAt(Instant at) { this.rejectedAt = at; return this; }
    public ThrottleLog withTokensRemaining(Double tokens) { this.tokensRemaining = tokens; return this; }

    // --- Getters ---
    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public String getThrottleKey() { return throttleKey; }
    public String getUserId() { return userId; }
    public String getIpAddress() { return ipAddress; }
    public String getApiEndpoint() { return apiEndpoint; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Double getTokensRemaining() { return tokensRemaining; }
}
