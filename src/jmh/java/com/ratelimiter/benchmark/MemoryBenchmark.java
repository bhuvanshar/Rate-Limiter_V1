package com.ratelimiter.benchmark;

import com.ratelimiter.core.TokenBucketRateLimiter;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.InMemoryCounterStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Memory footprint benchmark.
 *
 * Measures heap usage before and after populating N bucket entries.
 * Validates the claim of ~200 bytes per entry and ~20MB for 100K entries.
 *
 * Run with GC profiler to get accurate allocation data:
 *   java -jar target/benchmarks.jar MemoryBenchmark -prof gc
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgs = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class MemoryBenchmark {

    @Param({"1000", "10000", "50000", "100000"})
    private int entryCount;

    @Benchmark
    public long measureHeapForNEntries() {
        InMemoryCounterStore store = new InMemoryCounterStore();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(store);

        RateLimitRule rule = new RateLimitRule();
        rule.setRuleName("mem-bench");
        rule.setMaxRequests(100);
        rule.setWindowSeconds(60);
        rule.setBurstCapacity(100);

        // Force GC before measurement
        System.gc();
        long heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Populate entries
        for (int i = 0; i < entryCount; i++) {
            limiter.tryConsume("mem-key-" + i, rule);
        }

        // Force GC and measure
        System.gc();
        long heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long usedBytes = heapAfter - heapBefore;
        long bytesPerEntry = usedBytes / entryCount;

        System.out.printf("Entries: %,d | Heap delta: %,d bytes | Per entry: %d bytes | Store estimate: %,d bytes%n",
                entryCount, usedBytes, bytesPerEntry, store.estimateMemoryBytes());

        return usedBytes;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MemoryBenchmark.class.getSimpleName())
                .forks(1)
                .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}
