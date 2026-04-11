#!/bin/bash
# ============================================================
# JMH Benchmark Runner
#
# Compiles and runs all JMH benchmarks.
# Results are saved to jmh-results/ in both text and JSON format.
#
# Usage:
#   ./scripts/run-jmh.sh                    # Run all benchmarks
#   ./scripts/run-jmh.sh TokenBucket        # Run specific benchmark
#   ./scripts/run-jmh.sh Memory -prof gc    # Run with GC profiler
# ============================================================

set -euo pipefail

FILTER=${1:-""}
EXTRA_ARGS="${@:2}"
RESULTS_DIR="jmh-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "$RESULTS_DIR"

echo "============================================"
echo "JMH Benchmark Suite — API Rate Limiter"
echo "============================================"

# Step 1: Compile the project with JMH dependencies
echo ""
echo "Step 1: Compiling project..."
mvn clean package -DskipTests -q

# Step 2: Compile JMH benchmarks
echo "Step 2: Compiling JMH benchmarks..."
# JMH benchmarks need to be compiled separately with the JMH annotation processor
mvn compile -pl . -Pjmh -q 2>/dev/null || {
    echo ""
    echo "NOTE: JMH Maven profile not configured. Running benchmarks directly..."
    echo "Compiling JMH sources with javac..."

    # Compile JMH benchmarks against the project classes
    JMH_CP=$(mvn dependency:build-classpath -q -DincludeScope=compile -Dmdep.outputFile=/dev/stdout 2>/dev/null)
    PROJECT_CP="target/classes"

    javac -cp "$PROJECT_CP:$JMH_CP" \
        -d target/jmh-classes \
        src/jmh/java/com/ratelimiter/benchmark/*.java 2>/dev/null || true
}

# Step 3: Run benchmarks
echo "Step 3: Running benchmarks..."
echo ""

RESULT_TXT="${RESULTS_DIR}/jmh_${TIMESTAMP}.txt"
RESULT_JSON="${RESULTS_DIR}/jmh_${TIMESTAMP}.json"

# Run as standalone Java application using the benchmark main classes
if [ -n "$FILTER" ]; then
    echo "Filter: $FILTER"
    echo ""
fi

# Use Maven exec to run benchmarks (simplest cross-platform approach)
mvn exec:java \
    -Dexec.mainClass="org.openjdk.jmh.Main" \
    -Dexec.classpathScope=test \
    -Dexec.args="${FILTER} -rf json -rff ${RESULT_JSON} ${EXTRA_ARGS}" \
    2>&1 | tee "$RESULT_TXT"

echo ""
echo "============================================"
echo "Results saved:"
echo "  Text:  $RESULT_TXT"
echo "  JSON:  $RESULT_JSON"
echo "============================================"
echo ""
echo "Key metrics to verify:"
echo "  TokenBucketBenchmark.singleKey_singleThread  — should be <1 us/op"
echo "  TokenBucketBenchmark.multiKey_8threads        — should show linear scaling"
echo "  EndToEndBenchmark.threeRules_32threads         — should be <5 us/op"
echo "  KeyResolverBenchmark.resolveFullComposite      — should be <500 ns/op"
echo "  MemoryBenchmark (100K entries)                 — should be <25 MB"
