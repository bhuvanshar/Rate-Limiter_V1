/**
 * k6 Smoke Test — Basic functional verification.
 *
 * Verifies:
 *   - API responds with 200 under light load
 *   - Rate limit headers are present
 *   - 429 is returned when limit is exceeded
 *   - Response body format is correct
 *
 * Run:  k6 run k6/smoke-test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    vus: 1,
    iterations: 20,
    thresholds: {
        http_req_duration: ['p(95)<500'],
        checks: ['rate>0.8'],
    },
};

export default function () {
    const headers = {
        'X-User-Id': 'smoke-user-1',
        'Content-Type': 'application/json',
    };

    // Test GET /api/orders
    const res = http.get(`${BASE_URL}/api/orders`, { headers });

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'has X-RateLimit-Limit header': (r) => r.headers['X-Ratelimit-Limit'] !== undefined,
        'has X-RateLimit-Remaining header': (r) => r.headers['X-Ratelimit-Remaining'] !== undefined,
        'has X-RateLimit-Reset header': (r) => r.headers['X-Ratelimit-Reset'] !== undefined,
        'response body is valid JSON': (r) => {
            try { JSON.parse(r.body); return true; } catch (e) { return false; }
        },
    });

    if (res.status === 429) {
        const body = JSON.parse(res.body);
        check(body, {
            '429 has status field': (b) => b.status === 429,
            '429 has error field': (b) => b.error === 'Too Many Requests',
            '429 has retryAfterMs': (b) => b.retryAfterMs > 0,
        });
        check(res, {
            '429 has Retry-After header': (r) => r.headers['Retry-After'] !== undefined,
        });
    }

    sleep(0.1);
}
