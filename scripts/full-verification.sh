#!/bin/bash
# ============================================================
# FULL VERIFICATION PIPELINE
#
# Runs the complete verification suite in order:
#   Phase 1: Unit + Integration Tests (Maven)
#   Phase 2: JMH Microbenchmarks
#   Phase 3: Start app with JFR profiling
#   Phase 4: k6 Load Tests (smoke -> load -> accuracy -> stress)
#   Phase 5: Collect and summarize results
#
# Prerequisites:
#   - Java 17+
#   - Maven 3.8+
#   - k6 (https://k6.io/docs/get-started/installation/)
#   - MySQL running with rate_limiter_db created
#
# Usage: ./scripts/full-verification.sh
# ============================================================

set -euo pipefail

RESULTS_DIR="verification-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT="${RESULTS_DIR}/verification_${TIMESTAMP}.txt"

mkdir -p "$RESULTS_DIR"

echo "================================================================" | tee "$REPORT"
echo "API Rate Limiter — Full Verification Report" | tee -a "$REPORT"
echo "Date: $(date)" | tee -a "$REPORT"
echo "Java: $(java -version 2>&1 | head -1)" | tee -a "$REPORT"
echo "================================================================" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

# ---- PHASE 1: Unit + Integration Tests ----
echo "=== PHASE 1: Unit + Integration Tests ===" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

mvn clean test 2>&1 | tail -20 | tee -a "$REPORT"
TESTS_EXIT=$?

if [ $TESTS_EXIT -eq 0 ]; then
    echo "PHASE 1 RESULT: PASS" | tee -a "$REPORT"
else
    echo "PHASE 1 RESULT: FAIL (exit code $TESTS_EXIT)" | tee -a "$REPORT"
    echo "Fix failing tests before proceeding." | tee -a "$REPORT"
    exit 1
fi
echo "" | tee -a "$REPORT"

# ---- PHASE 2: JMH Benchmarks ----
echo "=== PHASE 2: JMH Microbenchmarks ===" | tee -a "$REPORT"
echo "(This takes ~10 minutes)" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

# Build the project jar first
mvn package -DskipTests -q

# Run benchmarks inline (simplified for single-command execution)
java -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -q -DincludeScope=test -Dmdep.outputFile=/dev/stdout)" \
    com.ratelimiter.benchmark.TokenBucketBenchmark 2>&1 | tail -30 | tee -a "$REPORT" || echo "JMH run requires jmh dependencies in classpath" | tee -a "$REPORT"

echo "" | tee -a "$REPORT"

# ---- PHASE 3: Start App with JFR ----
echo "=== PHASE 3: Start Application with JFR ===" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

JFR_FILE="${RESULTS_DIR}/ratelimiter_${TIMESTAMP}.jfr"

mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="-XX:StartFlightRecording=name=Verify,settings=profile,dumponexit=true,filename=$JFR_FILE,maxsize=500m -Xms512m -Xmx512m -XX:+UseG1GC" \
    &
APP_PID=$!

echo "App started with PID=$APP_PID, JFR recording to $JFR_FILE" | tee -a "$REPORT"
echo "Waiting for app to be ready..." | tee -a "$REPORT"

# Wait for app readiness
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "App ready after ${i}s" | tee -a "$REPORT"
        break
    fi
    sleep 1
done
echo "" | tee -a "$REPORT"

# ---- PHASE 4: k6 Load Tests ----
echo "=== PHASE 4: k6 Load Tests ===" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

echo "--- 4a: Smoke Test ---" | tee -a "$REPORT"
k6 run k6/smoke-test.js 2>&1 | tail -30 | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

echo "--- 4b: Accuracy Test ---" | tee -a "$REPORT"
k6 run k6/accuracy-test.js 2>&1 | tail -20 | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

echo "--- 4c: Load Test (5000 VUs) ---" | tee -a "$REPORT"
k6 run --out json="${RESULTS_DIR}/k6_load_${TIMESTAMP}.json" k6/load-test.js 2>&1 | tail -40 | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

echo "--- 4d: Stress Test (20000 VUs) ---" | tee -a "$REPORT"
k6 run --out json="${RESULTS_DIR}/k6_stress_${TIMESTAMP}.json" k6/stress-test.js 2>&1 | tail -40 | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

# ---- PHASE 5: Collect Results ----
echo "=== PHASE 5: Shutdown + Results Collection ===" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

# Stop the app (triggers JFR dump)
kill $APP_PID 2>/dev/null
wait $APP_PID 2>/dev/null || true
echo "App stopped. JFR recording saved to: $JFR_FILE" | tee -a "$REPORT"

# Analyze JFR if possible
if [ -f "$JFR_FILE" ] && command -v jfr &> /dev/null; then
    echo "" | tee -a "$REPORT"
    echo "--- JFR GC Summary ---" | tee -a "$REPORT"
    jfr print --events jdk.GCPhasePause "$JFR_FILE" 2>/dev/null | head -20 | tee -a "$REPORT"
fi

echo "" | tee -a "$REPORT"
echo "================================================================" | tee -a "$REPORT"
echo "VERIFICATION COMPLETE" | tee -a "$REPORT"
echo "Report: $REPORT" | tee -a "$REPORT"
echo "JFR:    $JFR_FILE" | tee -a "$REPORT"
echo "================================================================" | tee -a "$REPORT"
