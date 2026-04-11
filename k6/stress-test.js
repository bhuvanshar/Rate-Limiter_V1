/**
 * k6 Stress Test — Breaking point discovery.
 *
 * Ramps far beyond the 5,000 user target to find the system's
 * breaking point. Monitors for:
 *   - Latency degradation curve
 *   - Error rate increase
 *   - Memory pressure symptoms (GC pauses visible as latency spikes)
 *   - Connection pool exhaustion
 *
 * Run:  k6 run k6/stress-test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const overheadTrend = new Trend('overhead_ms');

export const options = {
    stages: [
        { duration: '1m',  target: 1000 },
        { duration: '1m',  target: 5000 },
        { duration: '1m',  target: 10000 },
        { duration: '1m',  target: 15000 },
        { duration: '1m',  target: 20000 },  // Well beyond target
        { duration: '2m',  target: 20000 },  // Sustain at peak
        { duration: '1m',  target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.05'],  // Allow up to 5% failure at extreme load
    },
};

export default function () {
    const headers = { 'X-User-Id': `stress-user-${__VU}` };
    const res = http.get(`${BASE_URL}/api/orders`, { headers });

    overheadTrend.add(res.timings.duration);

    check(res, {
        'no 5xx errors': (r) => r.status < 500,
        'valid response': (r) => r.status === 200 || r.status === 429,
    });

    sleep(Math.random() * 0.05);
}

export function handleSummary(data) {
    const p50 = data.metrics.http_req_duration.values['p(50)'];
    const p95 = data.metrics.http_req_duration.values['p(95)'];
    const p99 = data.metrics.http_req_duration.values['p(99)'];
    const max = data.metrics.http_req_duration.values['max'];
    const total = data.metrics.http_reqs.values.count;
    const failed = data.metrics.http_req_failed.values.rate;

    console.log('\n=== STRESS TEST SUMMARY ===');
    console.log(`Total requests:  ${total}`);
    console.log(`Failure rate:    ${(failed * 100).toFixed(2)}%`);
    console.log(`p50 latency:     ${p50.toFixed(2)}ms`);
    console.log(`p95 latency:     ${p95.toFixed(2)}ms`);
    console.log(`p99 latency:     ${p99.toFixed(2)}ms`);
    console.log(`Max latency:     ${max.toFixed(2)}ms`);
    console.log('===========================\n');

    return {};
}
