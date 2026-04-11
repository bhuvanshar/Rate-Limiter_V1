package com.ratelimiter.benchmark;

import com.ratelimiter.core.RateLimitAlgorithm;
import com.ratelimiter.core.ThrottleDecision;
import com.ratelimiter.core.TokenBucketRateLimiter;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.InMemoryCounterStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH benchmarks for the Token Bucket rate limiter.
 *
 * Measures:
 *   - Single-thread throughput (ops/sec)
 *   - Per-operation latency (ns/op)
 *   - Multi-threaded throughput under contention
 *   - Memory allocation rate
 *
 * Run with:
 *   java -jar target/benchmarks.jar TokenBucketBenchmark
 *
 * Or via Maven:
 *   mvn clean package -P jmh
 *   java -jar target/benchmarks.jar
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms512m", "-Xmx512m"})
public class TokenBucketBenchmark {

    private InMemoryCounterStore counterStore;
    private TokenBucketRateLimiter rateLimiter;
    private RateLimitRule highCapacityRule;
    private RateLimitRule lowCapacityRule;
    private AtomicLong keyCounter;

    @Setup(Level.Trial)
    public void setup() {
        counterStore = new InMemoryCounterStore();
        rateLimiter = new TokenBucketRateLimiter(counterStore);

        highCapacityRule = new RateLimitRule();
        highCapacityRule.setRuleName("bench-high");
        highCapacityRule.setMaxRequests(1_000_000);
        highCapacityRule.setWindowSeconds(3600);
        highCapacityRule.setBurstCapacity(1_000_000);

        lowCapacityRule = new RateLimitRule();
        lowCapacityRule.setRuleName("bench-low");
        lowCapacityRule.setMaxRequests(100);
        lowCapacityRule.setWindowSeconds(60);
        lowCapacityRule.setBurstCapacity(100);

        keyCounter = new AtomicLong(0);

        // Pre-populate 10K keys to simulate real workload
        for (int i = 0; i < 10_000; i++) {
            counterStore.getOrCreateBucket("preload-key-" + i, 1_000_000);
        }
    }

    /**
     * Benchmark 1: Single-key throughput.
     * All threads hammer the same key — maximum CAS contention.
     * This is the worst case for the token bucket.
     */
    @Benchmark
    @Threads(1)
    public void singleKey_singleThread(Blackhole bh) {
        bh.consume(rateLimiter.tryConsume("single-key", highCapacityRule));
    }

    @Benchmark
    @Threads(4)
    public void singleKey_4threads(Blackhole bh) {
        bh.consume(rateLimiter.tryConsume("contention-key", highCapacityRule));
    }

    @Benchmark
    @Threads(16)
    public void singleKey_16threads(Blackhole bh) {
        bh.consume(rateLimiter.tryConsume("hot-key", highCapacityRule));
    }

    /**
     * Benchmark 2: Multi-key throughput (realistic).
     * Each invocation uses a different key — minimal contention.
     * Simulates 5000+ distinct users hitting the limiter.
     */
    @Benchmark
    @Threads(1)
    public void multiKey_singleThread(Blackhole bh) {
        long id = keyCounter.incrementAndGet() % 10_000;
        bh.consume(rateLimiter.tryConsume("user-" + id, highCapacityRule));
    }

    @Benchmark
    @Threads(8)
    public void multiKey_8threads(Blackhole bh) {
        long id = keyCounter.incrementAndGet() % 10_000;
        bh.consume(rateLimiter.tryConsume("user-" + id, highCapacityRule));
    }

    @Benchmark
    @Threads(32)
    public void multiKey_32threads(Blackhole bh) {
        long id = keyCounter.incrementAndGet() % 10_000;
        bh.consume(rateLimiter.tryConsume("user-" + id, highCapacityRule));
    }

    /**
     * Benchmark 3: Rejection path.
     * Measures latency when the bucket is exhausted and returns rejection.
     * Important: rejection should be as fast as acceptance.
     */
    @Benchmark
    @Threads(4)
    public void rejectionPath(Blackhole bh) {
        // lowCapacityRule has 100 max, so after warm-up most calls will be rejections
        bh.consume(rateLimiter.tryConsume("exhausted-key", lowCapacityRule));
    }

    /**
     * Benchmark 4: Key creation (cold start).
     * Measures the cost of computeIfAbsent for new keys.
     */
    @Benchmark
    @Threads(4)
    public void newKeyCreation(Blackhole bh) {
        long id = keyCounter.incrementAndGet();
        bh.consume(rateLimiter.tryConsume("new-key-" + id, highCapacityRule));
    }

    /**
     * Benchmark 5: Memory pressure test.
     * Measures throughput with a large working set (10K pre-populated keys).
     */
    @Benchmark
    @Threads(8)
    public void largeWorkingSet(Blackhole bh) {
        long id = keyCounter.incrementAndGet() % 10_000;
        bh.consume(rateLimiter.tryConsume("preload-key-" + id, highCapacityRule));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TokenBucketBenchmark.class.getSimpleName())
                .forks(2)
                .build();
        new Runner(opt).run();
    }
}
