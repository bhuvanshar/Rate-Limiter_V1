#!/bin/bash
# ============================================================
# JFR Recording Analysis Script
#
# Generates a text report from a .jfr recording file.
# Useful when JDK Mission Control is not available.
#
# Usage: ./scripts/jfr-analyze.sh jfr-recordings/recording.jfr
# ============================================================

set -euo pipefail

JFR_FILE=${1:-""}
if [ -z "$JFR_FILE" ] || [ ! -f "$JFR_FILE" ]; then
    echo "Usage: $0 <recording.jfr>"
    echo "Available recordings:"
    ls -la jfr-recordings/*.jfr 2>/dev/null || echo "  (none found)"
    exit 1
fi

REPORT_FILE="${JFR_FILE%.jfr}_report.txt"
echo "Analyzing: $JFR_FILE"
echo "Report:    $REPORT_FILE"
echo ""

{
    echo "================================================================"
    echo "JFR Analysis Report — API Rate Limiter"
    echo "Recording: $JFR_FILE"
    echo "Generated: $(date)"
    echo "================================================================"
    echo ""

    echo "=== 1. CPU HOTSPOTS (Top Execution Samples) ==="
    echo ""
    jfr print --events jdk.ExecutionSample --stack-depth 10 "$JFR_FILE" 2>/dev/null | head -100
    echo ""

    echo "=== 2. GC ACTIVITY ==="
    echo ""
    echo "--- GC Pauses ---"
    jfr print --events jdk.GCPhasePause "$JFR_FILE" 2>/dev/null | head -40
    echo ""
    echo "--- Heap Summary ---"
    jfr print --events jdk.GCHeapSummary "$JFR_FILE" 2>/dev/null | head -30
    echo ""
    echo "--- G1 Heap Region Info ---"
    jfr print --events jdk.G1HeapSummary "$JFR_FILE" 2>/dev/null | head -20
    echo ""

    echo "=== 3. THREAD CONTENTION ==="
    echo ""
    echo "--- Java Monitor Wait (Lock Contention) ---"
    jfr print --events jdk.JavaMonitorWait "$JFR_FILE" 2>/dev/null | head -40
    echo ""
    echo "--- Java Monitor Enter (Lock Acquisition) ---"
    jfr print --events jdk.JavaMonitorEnter "$JFR_FILE" 2>/dev/null | head -40
    echo ""
    echo "--- Thread Park ---"
    jfr print --events jdk.ThreadPark "$JFR_FILE" 2>/dev/null | head -30
    echo ""

    echo "=== 4. MEMORY ALLOCATION ==="
    echo ""
    echo "--- Object Allocation in New TLAB ---"
    jfr print --events jdk.ObjectAllocationInNewTLAB "$JFR_FILE" 2>/dev/null | head -50
    echo ""
    echo "--- Object Allocation Outside TLAB ---"
    jfr print --events jdk.ObjectAllocationOutsideTLAB "$JFR_FILE" 2>/dev/null | head -30
    echo ""

    echo "=== 5. I/O ACTIVITY ==="
    echo ""
    echo "--- Socket Read ---"
    jfr print --events jdk.SocketRead "$JFR_FILE" 2>/dev/null | head -20
    echo ""
    echo "--- Socket Write ---"
    jfr print --events jdk.SocketWrite "$JFR_FILE" 2>/dev/null | head -20
    echo ""

    echo "=== 6. JVM INFO ==="
    echo ""
    jfr print --events jdk.JVMInformation "$JFR_FILE" 2>/dev/null | head -30
    echo ""
    jfr print --events jdk.CPUInformation "$JFR_FILE" 2>/dev/null | head -20
    echo ""
    jfr print --events jdk.OSInformation "$JFR_FILE" 2>/dev/null | head -10
    echo ""

    echo "================================================================"
    echo "END OF REPORT"
    echo "For visual analysis, open $JFR_FILE in JDK Mission Control (JMC)"
    echo "================================================================"

} > "$REPORT_FILE" 2>&1

echo "Report saved to: $REPORT_FILE"
echo ""
echo "Key sections:"
echo "  1. CPU Hotspots     — Should show ConcurrentHashMap/AtomicLong ops, NOT synchronized blocks"
echo "  2. GC Activity      — Should show <10ms p99 GC pauses with G1"
echo "  3. Thread Contention — Should show ZERO JavaMonitorEnter from rate limiter classes"
echo "  4. Memory Allocation — Should show minimal allocation per request"
echo "  5. I/O Activity     — Should show NO DB I/O on the hot path"
