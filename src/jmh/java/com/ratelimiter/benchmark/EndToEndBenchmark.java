package com.ratelimiter.benchmark;

import com.ratelimiter.core.*;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.InMemoryCounterStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * End-to-end benchmark simulating the full evaluation pipeline:
 *   key resolution -> strategy lookup -> token bucket consume
 *
 * This measures the real per-request overhead excluding Spring/HTTP
 * overhead. The claim is <5ms added latency; we expect <50 microseconds.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms512m", "-Xmx512m"})
public class EndToEndBenchmark {

    private KeyResolver keyResolver;
    private StrategyRegistry strategyRegistry;
    private RateLimitRule userRule;
    private RateLimitRule ipRule;
    private RateLimitRule userApiRule;
    private AtomicLong userCounter;

    @Setup(Level.Trial)
    public void setup() {
        InMemoryCounterStore store = new InMemoryCounterStore();
        keyResolver = new KeyResolver();

        TokenBucketRateLimiter tokenBucket = new TokenBucketRateLimiter(store);
        SlidingWindowRateLimiter slidingWindow = new SlidingWindowRateLimiter(store);
        strategyRegistry = new StrategyRegistry(List.of(tokenBucket, slidingWindow));

        userRule = createRule("user-global", RateLimitScope.USER, null,
                RateLimitAlgorithm.TOKEN_BUCKET, 1_000_000, 3600);
        ipRule = createRule("ip-global", RateLimitScope.IP, null,
                RateLimitAlgorithm.TOKEN_BUCKET, 1_000_000, 3600);
        userApiRule = createRule("user-api", RateLimitScope.USER_API, "GET:/api/orders",
                RateLimitAlgorithm.TOKEN_BUCKET, 1_000_000, 3600);

        userCounter = new AtomicLong(0);
    }

    /**
     * Simulates evaluating a single rule (the most common case).
     */
    @Benchmark
    @Threads(1)
    public void singleRule_singleThread(Blackhole bh) {
        long uid = userCounter.incrementAndGet() % 5000;
        RequestContext ctx = new RequestContext("user-" + uid, "10.0.0.1", "GET", "/api/orders");
        String key = keyResolver.resolve(userRule.getScope(), ctx);
        RateLimiterStrategy strategy = strategyRegistry.getStrategy(userRule.getAlgorithm());
        bh.consume(strategy.tryConsume(key, userRule));
    }

    /**
     * Simulates evaluating 3 rules per request (user + IP + user-API).
     * This is the realistic scenario where multiple rules apply.
     */
    @Benchmark
    @Threads(8)
    public void threeRules_8threads(Blackhole bh) {
        long uid = userCounter.incrementAndGet() % 5000;
        RequestContext ctx = new RequestContext("user-" + uid, "10.0.0." + (uid % 256), "GET", "/api/orders");

        // Evaluate all 3 rules (same as RateLimitService.evaluate)
        RateLimitRule[] rules = {userRule, ipRule, userApiRule};
        for (RateLimitRule rule : rules) {
            String key = keyResolver.resolve(rule.getScope(), ctx);
            if (key != null) {
                RateLimiterStrategy strategy = strategyRegistry.getStrategy(rule.getAlgorithm());
                ThrottleDecision decision = strategy.tryConsume(key, rule);
                bh.consume(decision);
                if (!decision.allowed()) break;
            }
        }
    }

    /**
     * Maximum contention: 32 threads evaluating 3 rules each.
     */
    @Benchmark
    @Threads(32)
    public void threeRules_32threads(Blackhole bh) {
        long uid = userCounter.incrementAndGet() % 5000;
        RequestContext ctx = new RequestContext("user-" + uid, "10.0.0." + (uid % 256), "GET", "/api/orders");

        RateLimitRule[] rules = {userRule, ipRule, userApiRule};
        for (RateLimitRule rule : rules) {
            String key = keyResolver.resolve(rule.getScope(), ctx);
            if (key != null) {
                RateLimiterStrategy strategy = strategyRegistry.getStrategy(rule.getAlgorithm());
                ThrottleDecision decision = strategy.tryConsume(key, rule);
                bh.consume(decision);
                if (!decision.allowed()) break;
            }
        }
    }

    private RateLimitRule createRule(String name, RateLimitScope scope, String apiPattern,
                                     RateLimitAlgorithm algo, int max, int window) {
        RateLimitRule rule = new RateLimitRule();
        rule.setRuleName(name);
        rule.setScope(scope);
        rule.setApiPattern(apiPattern);
        rule.setAlgorithm(algo);
        rule.setMaxRequests(max);
        rule.setWindowSeconds(window);
        rule.setBurstCapacity(max);
        rule.setEnabled(true);
        return rule;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(EndToEndBenchmark.class.getSimpleName())
                .forks(2)
                .build();
        new Runner(opt).run();
    }
}
