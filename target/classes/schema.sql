-- ============================================================
-- Rate Limiter Database Schema
-- MySQL 8.0+
-- ============================================================

CREATE TABLE IF NOT EXISTS rate_limit_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name       VARCHAR(128) NOT NULL,
    scope           VARCHAR(32)  NOT NULL COMMENT 'USER, IP, API, USER_API, IP_API, USER_IP, USER_IP_API',
    api_pattern     VARCHAR(256) DEFAULT NULL COMMENT 'e.g. GET:/api/orders, POST:/api/*, null = match all',
    algorithm       VARCHAR(32)  NOT NULL DEFAULT 'TOKEN_BUCKET' COMMENT 'TOKEN_BUCKET or SLIDING_WINDOW',
    max_requests    INT          NOT NULL COMMENT 'Max requests per window (or bucket refill amount)',
    window_seconds  INT          NOT NULL COMMENT 'Window duration or refill period in seconds',
    burst_capacity  INT          DEFAULT NULL COMMENT 'Token bucket burst cap; defaults to max_requests if null',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    priority        INT          NOT NULL DEFAULT 0 COMMENT 'Higher = evaluated first',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_scope_enabled (scope, enabled),
    INDEX idx_api_pattern (api_pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS throttle_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id          BIGINT       NOT NULL,
    rule_name        VARCHAR(128),
    throttle_key     VARCHAR(512) NOT NULL,
    user_id          VARCHAR(128),
    ip_address       VARCHAR(45),
    api_endpoint     VARCHAR(256),
    rejected_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tokens_remaining DOUBLE,

    INDEX idx_rejected_at (rejected_at),
    INDEX idx_user_id (user_id),
    INDEX idx_throttle_key (throttle_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
