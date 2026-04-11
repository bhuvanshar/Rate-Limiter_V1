# API Rate Limiter — Production Design Document

## 1. Problem Framing

Every production API faces the same class of problems: a single misbehaving client can exhaust resources meant for thousands, a sudden traffic spike can cascade into full-service failure, and without granular control you're left choosing between over-provisioning or under-serving. Rate limiting is the gatekeeper that prevents these scenarios.

**Business goals:**

- Protect backend services from abuse and traffic spikes.
- Provide fair access across users, IPs, and endpoints.
- Give operators fine-grained, database-driven control over throttle rules without code deployments.
- Maintain sub-5ms added latency so the limiter never becomes the bottleneck.

**Engineering goals:**

- Support composite throttle keys (userId, IP, API, and all combinations).
- Provide an extensible strategy pattern so new algorithms (sliding window, leaky bucket) can be added without touching the core pipeline.
- Keep the hot path entirely in-memory; use MySQL only for rule configuration and audit logging.
- Design the counter store interface so a Redis implementation can be swapped in later for distributed deployments.
- Handle 5,000+ concurrent users with thread-safe, lock-free data structures.

---

## 2. Algorithm Analysis and Selection

### Fixed Window Counter

How it works: Divides time into fixed intervals (e.g., each minute). A counter increments for each request within the window. Resets at the boundary.

Pros: Extremely simple. O(1) time and space per key.

Cons: The "boundary burst" problem — a client can send `maxRequests` at 0:59 and again at 1:00, effectively doubling the intended rate over a 2-second span.

### Sliding Window Log

How it works: Stores a timestamp for every request. To check the limit, count timestamps within `[now - window, now]`.

Pros: Perfectly accurate. No boundary artifacts.

Cons: O(n) memory per key where n is the number of requests in the window. At 100 req/min across 50K active keys, that's 5M timestamps in memory. Unacceptable for high-traffic systems.

### Sliding Window Counter

How it works: A hybrid. Splits the window into sub-windows and weights the previous window's count by the fraction of overlap. For example, if we're 40% into the current minute, the effective count is `currentCount + previousCount * 0.6`.

Pros: Good accuracy with O(1) space per key. Eliminates most boundary burst issues.

Cons: Slightly more complex than fixed window. Still an approximation.

### Token Bucket

How it works: Each key has a bucket with a maximum capacity. Tokens are added at a steady rate (the refill rate). Each request consumes one token. If the bucket is empty, the request is rejected. Tokens accumulate up to the bucket capacity, which naturally allows short bursts.

Pros: Naturally handles burst traffic (a client that was idle can use accumulated tokens). Smooth rate enforcement. O(1) time and space. Widely used in production (AWS API Gateway, Stripe, Cloudflare). The refill can be computed lazily — no background thread needed.

Cons: Slightly more state per key (tokens remaining + last refill timestamp) compared to a simple counter.

### Decision: Token Bucket as primary, Sliding Window Counter as secondary

Token Bucket is the best fit for v1 because:

1. It handles bursts gracefully — real traffic is bursty, not uniform.
2. Lazy refill means no background threads for token replenishment.
3. The math is simple and entirely computed at check time: `tokens = min(capacity, tokens + elapsed * refillRate)`.
4. It maps cleanly to the user experience: "You get 100 requests per minute, and you can burst up to 100 at once if you haven't used any recently."
5. It's the industry standard for API rate limiting.

We also implement Sliding Window Counter as a second strategy to demonstrate the strategy pattern and to give operators a choice for use cases where strict per-window accounting is preferred.

---

## 3. Storage Architecture: Why Hybrid

**The core insight: separate the read-heavy hot path from the write-heavy configuration path.**

### Hot path (every request): In-Memory

Counters/token bucket state lives in a `ConcurrentHashMap`. This gives us:

- O(1) lookup per request.
- No network round-trip.
- No database connection pool pressure.
- Achievable sub-microsecond overhead.

The tradeoff: state is local to the JVM instance. In a single-node deployment this is fine. For multi-node, we design the `CounterStore` interface so a `RedisCounterStore` can replace `InMemoryCounterStore` with zero changes to the service layer.

### Configuration path (infrequent): MySQL + Cache

Rate limit rules are stored in MySQL and loaded into a Caffeine cache with a configurable TTL (default 60s). This means:

- Rule changes take effect within the cache TTL — no restart needed.
- MySQL is hit at most once per TTL interval, not per request.
- The cache is read-through: on miss, it queries MySQL and populates.

### Audit path (asynchronous): MySQL

Throttled requests are logged asynchronously via a bounded queue and batch inserts. This keeps the hot path latency unaffected by database write performance.

---

## 4. Concurrency Design

### Why ConcurrentHashMap + computeIfAbsent + atomic operations

The `ConcurrentHashMap.computeIfAbsent` method is atomic for a given key — it guarantees that the lambda runs exactly once per key, even under concurrent access. Combined with `AtomicLong` and `AtomicReference` for token counts and timestamps, we get lock-free, thread-safe counters without any synchronized blocks.

**Per-key granularity:** Two requests for different keys never contend with each other. Two requests for the same key use CAS (compare-and-swap) operations on the atomic fields, which are wait-free on modern hardware.

**No global locks.** The only synchronization is at the individual bucket level via atomics. This means throughput scales linearly with CPU cores.

### Why not ReentrantLock or synchronized?

- `synchronized` on a shared map would serialize all requests — a dealbreaker at 5K concurrent users.
- `ReentrantLock` per key is viable but adds allocation overhead and requires careful lifecycle management (when do you remove the lock?). Atomics are simpler and faster.

### Expired Entry Cleanup

A scheduled task runs every 60 seconds (configurable) and evicts entries that haven't been accessed for longer than the maximum window duration. This prevents unbounded memory growth from one-time visitors.

---

## 5. Key Resolution Design

Rate limit rules can target different dimensions. The `KeyResolver` builds a composite string key from the request context:

| Rule Scope        | Key Pattern                        | Example                              |
|-------------------|------------------------------------|--------------------------------------|
| userId            | `user:{userId}`                    | `user:42`                            |
| IP                | `ip:{ip}`                          | `ip:192.168.1.1`                     |
| API               | `api:{method}:{path}`              | `api:GET:/api/orders`                |
| userId + API      | `user:{userId}:api:{method}:{path}`| `user:42:api:GET:/api/orders`        |
| IP + API          | `ip:{ip}:api:{method}:{path}`      | `ip:10.0.0.1:api:POST:/api/login`   |
| userId + IP       | `user:{userId}:ip:{ip}`           | `user:42:ip:10.0.0.1`               |
| userId + IP + API | `user:{userId}:ip:{ip}:api:{method}:{path}` | Full composite              |

Keys are deterministic and ordered so the same request always produces the same key regardless of the order dimensions are evaluated.

---

## 6. Filter vs Interceptor vs AOP Aspect

| Approach           | Runs at                  | Access to              | Can short-circuit before Spring MVC? |
|--------------------|--------------------------|------------------------|--------------------------------------|
| Servlet Filter     | Before DispatcherServlet | Raw HttpServletRequest | Yes                                  |
| HandlerInterceptor | After DispatcherServlet  | Handler method info    | Partially (preHandle)                |
| AOP Aspect         | Around method invocation | Method annotations     | Yes, but only at method level        |

**Decision: Servlet Filter (OncePerRequestFilter)**

- It runs earliest in the chain — before any Spring MVC processing, argument resolution, or serialization.
- It has access to the raw request (headers, IP, path) which is all we need for key resolution.
- It can reject requests with minimal overhead — the response is written directly, bypassing the entire Spring MVC pipeline.
- For the rate limiter use case, we don't need handler method metadata or annotation-level granularity.

If later we want per-method annotation support (`@RateLimit(max=10, window=60)`), we can add an AOP aspect as a complementary mechanism. The filter handles the global/rule-based limiting; the aspect handles method-level overrides.

---

## 7. Database Schema

```sql
CREATE TABLE rate_limit_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name       VARCHAR(128) NOT NULL,
    scope           VARCHAR(32) NOT NULL,  -- USER, IP, API, USER_API, IP_API, USER_IP, USER_IP_API
    api_pattern     VARCHAR(256),          -- e.g., GET:/api/orders, POST:/api/*, null for non-API scopes
    algorithm       VARCHAR(32) NOT NULL DEFAULT 'TOKEN_BUCKET',  -- TOKEN_BUCKET, SLIDING_WINDOW
    max_requests    INT NOT NULL,          -- bucket capacity or window max
    window_seconds  INT NOT NULL,          -- refill period or window size
    burst_capacity  INT,                   -- for token bucket: max burst (defaults to max_requests)
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    priority        INT NOT NULL DEFAULT 0, -- higher = evaluated first
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_scope_enabled (scope, enabled),
    INDEX idx_api_pattern (api_pattern)
);

CREATE TABLE throttle_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id         BIGINT NOT NULL,
    rule_name       VARCHAR(128),
    throttle_key    VARCHAR(512) NOT NULL,
    user_id         VARCHAR(128),
    ip_address      VARCHAR(45),
    api_endpoint    VARCHAR(256),
    rejected_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tokens_remaining DOUBLE,
    
    INDEX idx_rejected_at (rejected_at),
    INDEX idx_user_id (user_id),
    INDEX idx_throttle_key (throttle_key)
);
```

---

## 8. Class Diagram (text form)

```
┌─────────────────────┐     ┌──────────────────────┐
│  RateLimitFilter     │────>│  RateLimitService     │
│  (OncePerRequest)    │     │                      │
└─────────────────────┘     └──────────┬───────────┘
                                       │
                         ┌─────────────┼─────────────┐
                         │             │             │
                         v             v             v
               ┌──────────────┐ ┌───────────┐ ┌──────────────────┐
               │ RuleLoader   │ │ KeyResolver│ │ RateLimiterStrategy│
               │ Service      │ │           │ │   (interface)     │
               └──────┬───────┘ └───────────┘ └────────┬─────────┘
                      │                                 │
                      v                          ┌──────┴──────┐
               ┌──────────────┐                  │             │
               │ RateLimitRule│          ┌───────┴──┐  ┌───────┴──────┐
               │ Repository   │          │TokenBucket│  │SlidingWindow │
               │ (JPA)        │          │RateLimiter│  │RateLimiter   │
               └──────────────┘          └───────┬──┘  └───────┬──────┘
                                                 │             │
                                                 v             v
                                          ┌──────────────────────┐
                                          │  CounterStore        │
                                          │   (interface)        │
                                          └──────────┬───────────┘
                                                     │
                                              ┌──────┴──────┐
                                              │InMemory     │
                                              │CounterStore  │
                                              └─────────────┘
```

---

## 9. Request Sequence Flow

```
Client Request
     │
     ▼
[RateLimitFilter.doFilterInternal]
     │
     ├── Extract userId (from header/JWT), IP, API endpoint
     │
     ├── Call RateLimitService.checkRateLimit(context)
     │       │
     │       ├── RuleLoaderService.getRulesForRequest(context)
     │       │       └── Caffeine cache → MySQL (on cache miss)
     │       │
     │       ├── For each matching rule (ordered by priority):
     │       │       │
     │       │       ├── KeyResolver.resolve(rule.scope, context) → "user:42:api:GET:/api/orders"
     │       │       │
     │       │       ├── Select RateLimiterStrategy based on rule.algorithm
     │       │       │
     │       │       └── strategy.tryConsume(key, rule) → ThrottleDecision
     │       │               │
     │       │               └── CounterStore.getOrCreate(key) → atomic token bucket ops
     │       │
     │       └── Return first REJECTED decision, or ALLOWED if all pass
     │
     ├── If ALLOWED:
     │       ├── Set X-RateLimit-* response headers
     │       └── chain.doFilter() → proceed to controller
     │
     └── If REJECTED:
             ├── Set 429 status + Retry-After + X-RateLimit-* headers
             ├── Write JSON error body
             └── Async log to throttle_log table
```

---

## 10. Future Enhancements

1. **Redis-backed CounterStore**: Implement `RedisCounterStore` using Redis MULTI/EXEC or Lua scripts for atomic token bucket operations. Swap via Spring profile (`spring.profiles.active=distributed`).

2. **Dynamic rule refresh**: Add a `@Scheduled` method that polls a version counter in MySQL, or use Spring Cloud Bus / database change notification to invalidate the Caffeine cache immediately on rule changes.

3. **Per-tenant throttling**: Add a `tenant_id` column to `rate_limit_rule` and a TENANT scope. Resolve tenant from JWT claims or a separate header.

4. **Admin dashboard**: Expose Prometheus metrics (request counts, rejection rates, latency histograms per rule) via Micrometer. Build a Grafana dashboard.

5. **Circuit breaker integration**: When a specific key's rejection rate exceeds a threshold, trip a circuit breaker that fast-fails without even checking the token bucket — reducing CPU work during sustained attacks.

6. **Annotation-based limits**: Add `@RateLimit(max=10, window=60, scope=USER)` annotation support via AOP, complementing the filter-based global rules.

7. **Warm-up on startup**: On application boot, pre-populate the Caffeine rule cache so the first request doesn't pay the MySQL latency.

---

## 11. Why This Beats Hardcoded Limits

Hardcoded limits (e.g., `if (counter > 100) reject()`) fail in production because: (a) changing limits requires a code change and deployment, (b) there's no way to apply different limits to different users or endpoints without spaghetti if-else chains, (c) there's no visibility into who's being throttled and why, (d) the logic inevitably gets duplicated across services.

A database-driven, strategy-pattern design means operators can add, modify, or disable rules via SQL or an admin API without any deployment. The strategy pattern means the algorithm itself is a pluggable dimension — you can A/B test token bucket vs sliding window on different endpoints.

## 12. Common Mistakes in Rate Limiter Design

1. **Using the database for every check.** Even with connection pooling, a MySQL round-trip adds 0.5–2ms per request. At 5K concurrent users, that's 5K connections competing for the pool. Keep the hot path in memory.

2. **Using synchronized blocks on a shared map.** This serializes all requests through a single lock. Use ConcurrentHashMap + atomics instead.

3. **Not cleaning up expired entries.** Without eviction, memory grows unbounded. A user who made one request six hours ago still holds a bucket entry.

4. **Building keys by string concatenation in a loop.** Use StringBuilder or a well-defined key schema to avoid garbage collection pressure.

5. **Blocking on audit logging.** Writing throttle logs synchronously adds database latency to the rejection path. Use async logging.

6. **Ignoring the "first request" problem.** If the rule cache is cold, the first request pays full MySQL latency. Pre-warm on startup.

---

## 13. Performance Estimates

**Memory per bucket entry:** ~200 bytes (key string ~80 bytes, AtomicLong 24 bytes, AtomicReference 16 bytes, ConcurrentHashMap overhead ~80 bytes). For 100K active keys: ~20MB. Well within JVM heap.

**Overhead per request:** ConcurrentHashMap.get is ~50ns. Atomic CAS is ~10ns. Key string construction is ~200ns. Total hot path: <1μs. With Caffeine cache lookup for rules: ~2μs. Total overhead: well under 1ms, far below the 5ms budget.

**Throughput at 5K concurrent:** With no global locks and per-key atomics, throughput is bounded by CPU cores, not contention. On a 4-core machine, expect >100K decisions/second.
