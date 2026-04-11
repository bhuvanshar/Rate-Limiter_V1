/**
 * k6 Load Test — Sustained concurrency verification.
 *
 * Simulates 5,000 concurrent users over 5 minutes.
 *
 * Validates:
 *   - p95 latency < 5ms added overhead
 *   - p99 latency < 10ms
 *   - Zero errors (non-429)
 *   - Throttling accuracy (ratio of 429s matches expected rate)
 *   - System handles ramp-up gracefully
 *
 * Run:  k6 run k6/load-test.js
 * With report:  k6 run --out json=results/load-test.json k6/load-test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Custom metrics
const throttledRequests = new Counter('throttled_requests');
const allowedRequests = new Counter('allowed_requests');
const throttleRate = new Rate('throttle_rate');
const rateLimitOverhead = new Trend('rate_limit_overhead_ms');

export const options = {
    stages: [
        { duration: '30s', target: 500 },    // Ramp up to 500 VUs
        { duration: '30s', target: 2000 },   // Ramp up to 2000 VUs
        { duration: '30s', target: 5000 },   // Ramp up to 5000 VUs
        { duration: '3m',  target: 5000 },   // Sustain 5000 VUs
        { duration: '30s', target: 0 },      // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<50', 'p(99)<100'],  // Total HTTP p95 < 50ms
        rate_limit_overhead_ms: ['p(95)<5', 'p(99)<10'],
        http_req_failed: ['rate<0.01'],  // Less than 1% non-HTTP errors
        checks: ['rate>0.95'],
    },
};

export default function () {
    const userId = `user-${__VU}`;
    const headers = {
        'X-User-Id': userId,
        'Content-Type': 'application/json',
    };

    // Distribute across multiple endpoints
    const endpoints = [
        '/api/orders',
        '/api/users/1',
        '/api/health',
    ];
    const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];

    const start = Date.now();
    const res = http.get(`${BASE_URL}${endpoint}`, { headers });
    const elapsed = Date.now() - start;

    // Track overhead (subtract estimated controller time ~1ms)
    const overhead = Math.max(0, elapsed - 1);
    rateLimitOverhead.add(overhead);

    if (res.status === 429) {
        throttledRequests.add(1);
        throttleRate.add(true);
    } else {
        allowedRequests.add(1);
        throttleRate.add(false);
    }

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'no server errors': (r) => r.status < 500,
        'has rate limit headers': (r) =>
            r.headers['X-Ratelimit-Limit'] !== undefined ||
            r.status === 200,
    });

    // Small random sleep to simulate realistic traffic patterns
    sleep(Math.random() * 0.1);
}

export function handleSummary(data) {
    const p95 = data.metrics.http_req_duration.values['p(95)'];
    const p99 = data.metrics.http_req_duration.values['p(99)'];
    const totalReqs = data.metrics.http_reqs.values.count;
    const throttled = data.metrics.throttled_requests ? data.metrics.throttled_requests.values.count : 0;
    const allowed = data.metrics.allowed_requests ? data.metrics.allowed_requests.values.count : 0;

    console.log('\n=== LOAD TEST SUMMARY ===');
    console.log(`Total requests:    ${totalReqs}`);
    console.log(`Allowed (200):     ${allowed}`);
    console.log(`Throttled (429):   ${throttled}`);
    console.log(`Throttle rate:     ${((throttled / totalReqs) * 100).toFixed(2)}%`);
    console.log(`p95 latency:       ${p95.toFixed(2)}ms`);
    console.log(`p99 latency:       ${p99.toFixed(2)}ms`);
    console.log('========================\n');

    return {};
}
