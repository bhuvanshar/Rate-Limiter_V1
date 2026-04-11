package com.ratelimiter.benchmark;

import com.ratelimiter.core.SlidingWindowRateLimiter;
import com.ratelimiter.core.ThrottleDecision;
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
 * JMH benchmarks for the Sliding Window rate limiter.
 * Mirrors the Token Bucket benchmarks for direct comparison.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms512m", "-Xmx512m"})
public class SlidingWindowBenchmark {

    private InMemoryCounterStore counterStore;
    private SlidingWindowRateLimiter rateLimiter;
    private RateLimitRule highCapacityRule;
    private AtomicLong keyCounter;

    @Setup(Level.Trial)
    public void setup() {
        counterStore = new InMemoryCounterStore();
        rateLimiter = new SlidingWindowRateLimiter(counterStore);

        highCapacityRule = new RateLimitRule();
        highCapacityRule.setRuleName("sw-bench");
        highCapacityRule.setMaxRequests(1_000_000);
        highCapacityRule.setWindowSeconds(3600);

        keyCounter = new AtomicLong(0);

        for (int i = 0; i < 10_000; i++) {
            counterStore.getOrCreateWindow("sw-preload-" + i);
        }
    }

    @Benchmark
    @Threads(1)
    public void singleKey_singleThread(Blackhole bh) {
        bh.consume(rateLimiter.tryConsume("sw-single", highCapacityRule));
    }

    @Benchmark
    @Threads(4)
    public void singleKey_4threads(Blackhole bh) {
        bh.consume(rateLimiter.tryConsume("sw-contention", highCapacityRule));
    }

    @Benchmark
    @Threads(16)
    public void singleKey_16threads(Blackhole bh) {
        bh.consume(rateLimiter.tryConsume("sw-hot", highCapacityRule));
    }

    @Benchmark
    @Threads(8)
    public void multiKey_8threads(Blackhole bh) {
        long id = keyCounter.incrementAndGet() % 10_000;
        bh.consume(rateLimiter.tryConsume("sw-user-" + id, highCapacityRule));
    }

    @Benchmark
    @Threads(32)
    public void multiKey_32threads(Blackhole bh) {
        long id = keyCounter.incrementAndGet() % 10_000;
        bh.consume(rateLimiter.tryConsume("sw-user-" + id, highCapacityRule));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SlidingWindowBenchmark.class.getSimpleName())
                .forks(2)
                .build();
        new Runner(opt).run();
    }
}
