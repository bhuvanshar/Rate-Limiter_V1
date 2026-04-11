package com.ratelimiter.benchmark;

import com.ratelimiter.core.KeyResolver;
import com.ratelimiter.core.RateLimitScope;
import com.ratelimiter.core.RequestContext;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for key resolution.
 * Measures the overhead of building composite string keys
 * from request context. This runs on every single request,
 * so it must be extremely fast.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms256m", "-Xmx256m"})
public class KeyResolverBenchmark {

    private KeyResolver keyResolver;
    private RequestContext fullContext;
    private RequestContext anonymousContext;

    @Setup
    public void setup() {
        keyResolver = new KeyResolver();
        fullContext = new RequestContext("user-42", "192.168.1.100", "GET", "/api/orders");
        anonymousContext = new RequestContext(null, "10.0.0.1", "POST", "/api/login");
    }

    @Benchmark
    public void resolveUserKey(Blackhole bh) {
        bh.consume(keyResolver.resolve(RateLimitScope.USER, fullContext));
    }

    @Benchmark
    public void resolveIpKey(Blackhole bh) {
        bh.consume(keyResolver.resolve(RateLimitScope.IP, fullContext));
    }

    @Benchmark
    public void resolveUserApiComposite(Blackhole bh) {
        bh.consume(keyResolver.resolve(RateLimitScope.USER_API, fullContext));
    }

    @Benchmark
    public void resolveFullComposite(Blackhole bh) {
        bh.consume(keyResolver.resolve(RateLimitScope.USER_IP_API, fullContext));
    }

    @Benchmark
    public void resolveIpApi_anonymous(Blackhole bh) {
        bh.consume(keyResolver.resolve(RateLimitScope.IP_API, anonymousContext));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(KeyResolverBenchmark.class.getSimpleName())
                .forks(2)
                .build();
        new Runner(opt).run();
    }
}
