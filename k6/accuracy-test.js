/**
 * k6 Accuracy Test — Throttling correctness verification.
 *
 * This test verifies that the rate limiter enforces limits accurately.
 * A single user sends exactly N+5 requests within a window where the
 * limit is N. We then verify exactly N were allowed and 5 were rejected.
 *
 * Run:  k6 run k6/accuracy-test.js
 *
 * PREREQUISITE: Create a tight rule first via the admin API:
 *   curl -X POST http://localhost:8080/rate-limit/admin/rules \
 *     -H "Content-Type: application/json" \
 *     -d '{"ruleName":"accuracy-test","scope":"USER","algorithm":"TOKEN_BUCKET",
 *          "maxRequests":10,"windowSeconds":60,"burstCapacity":10,"enabled":true,"priority":100}'
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EXPECTED_LIMIT = parseInt(__ENV.LIMIT || '10');
const TOTAL_REQUESTS = EXPECTED_LIMIT + 5;

const allowed = new Counter('allowed_count');
const rejected = new Counter('rejected_count');

export const options = {
    vus: 1,
    iterations: TOTAL_REQUESTS,
    thresholds: {
        checks: ['rate==1.0'],  // All checks must pass
    },
};

export default function () {
    const headers = { 'X-User-Id': 'accuracy-test-user' };
    const res = http.get(`${BASE_URL}/api/orders`, { headers });

    if (res.status === 200) {
        allowed.add(1);
    } else if (res.status === 429) {
        rejected.add(1);
    }

    check(res, {
        'valid status': (r) => r.status === 200 || r.status === 429,
    });
}

export function handleSummary(data) {
    const allowedCount = data.metrics.allowed_count ? data.metrics.allowed_count.values.count : 0;
    const rejectedCount = data.metrics.rejected_count ? data.metrics.rejected_count.values.count : 0;

    const limitAccurate = allowedCount === EXPECTED_LIMIT;
    const rejectionAccurate = rejectedCount === (TOTAL_REQUESTS - EXPECTED_LIMIT);

    console.log('\n=== ACCURACY TEST RESULTS ===');
    console.log(`Expected limit:     ${EXPECTED_LIMIT}`);
    console.log(`Total requests:     ${TOTAL_REQUESTS}`);
    console.log(`Allowed (200):      ${allowedCount} ${limitAccurate ? 'PASS' : 'FAIL (expected ' + EXPECTED_LIMIT + ')'}`);
    console.log(`Rejected (429):     ${rejectedCount} ${rejectionAccurate ? 'PASS' : 'FAIL'}`);
    console.log(`Accuracy:           ${limitAccurate && rejectionAccurate ? 'PERFECT' : 'DEVIATION DETECTED'}`);
    console.log('=============================\n');

    return {};
}
