-- ============================================================
-- Sample Rate Limit Rules
-- These demonstrate the various scope/algorithm combinations.
-- Adjust values for your environment.
-- ============================================================

-- 1. Global per-user limit: 100 requests/minute
INSERT INTO rate_limit_rule (rule_name, scope, api_pattern, algorithm, max_requests, window_seconds, burst_capacity, enabled, priority)
VALUES ('global-user-limit', 'USER', NULL, 'TOKEN_BUCKET', 100, 60, 100, TRUE, 10)
ON DUPLICATE KEY UPDATE rule_name = rule_name;

-- 2. Global per-IP limit: 200 requests/minute
INSERT INTO rate_limit_rule (rule_name, scope, api_pattern, algorithm, max_requests, window_seconds, burst_capacity, enabled, priority)
VALUES ('global-ip-limit', 'IP', NULL, 'TOKEN_BUCKET', 200, 60, 200, TRUE, 10)
ON DUPLICATE KEY UPDATE rule_name = rule_name;

-- 3. Per-user per-API limit on orders: 20 requests/minute
INSERT INTO rate_limit_rule (rule_name, scope, api_pattern, algorithm, max_requests, window_seconds, burst_capacity, enabled, priority)
VALUES ('user-orders-limit', 'USER_API', 'GET:/api/orders', 'TOKEN_BUCKET', 20, 60, 20, TRUE, 20)
ON DUPLICATE KEY UPDATE rule_name = rule_name;

-- 4. Tight per-IP limit on login (brute-force protection): 5 requests/minute
INSERT INTO rate_limit_rule (rule_name, scope, api_pattern, algorithm, max_requests, window_seconds, burst_capacity, enabled, priority)
VALUES ('ip-login-limit', 'IP_API', 'POST:/api/login', 'TOKEN_BUCKET', 5, 60, 5, TRUE, 30)
ON DUPLICATE KEY UPDATE rule_name = rule_name;

-- 5. Most granular: per user+IP+API on POST orders: 10 requests/minute
INSERT INTO rate_limit_rule (rule_name, scope, api_pattern, algorithm, max_requests, window_seconds, burst_capacity, enabled, priority)
VALUES ('user-ip-create-order', 'USER_IP_API', 'POST:/api/orders', 'TOKEN_BUCKET', 10, 60, 10, TRUE, 40)
ON DUPLICATE KEY UPDATE rule_name = rule_name;

-- 6. Sliding window example: per-user on all /api/* endpoints
INSERT INTO rate_limit_rule (rule_name, scope, api_pattern, algorithm, max_requests, window_seconds, burst_capacity, enabled, priority)
VALUES ('user-api-sliding', 'USER_API', 'GET:/api/*', 'SLIDING_WINDOW', 50, 60, NULL, TRUE, 5)
ON DUPLICATE KEY UPDATE rule_name = rule_name;

-- 7. Per user+IP limit: 30 requests/minute
INSERT INTO rate_limit_rule (rule_name, scope, api_pattern, algorithm, max_requests, window_seconds, burst_capacity, enabled, priority)
VALUES ('user-ip-global', 'USER_IP', NULL, 'TOKEN_BUCKET', 30, 60, 30, TRUE, 15)
ON DUPLICATE KEY UPDATE rule_name = rule_name;
