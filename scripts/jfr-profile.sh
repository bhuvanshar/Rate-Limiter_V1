#!/bin/bash
# ============================================================
# JFR (Java Flight Recorder) Profiling Script
#
# Records a JFR profile of the running rate limiter application
# during a load test. Captures:
#   - CPU hotspots (method profiling)
#   - Memory allocation (object allocation sampling)
#   - Lock contention (monitor events)
#   - GC activity (GC pauses, heap usage)
#   - Thread activity (thread states, park/unpark)
#   - I/O events (socket/file I/O)
#
# Usage:
#   1. Start the app:  ./scripts/jfr-start-app.sh
#   2. Run load test:  k6 run k6/load-test.js
#   3. (Profile is captured automatically and saved)
#   4. Open with:      jfr print recording.jfr
#                  or:  Open in JDK Mission Control (JMC)
#
# Alternatively, attach to a running process:
#   ./scripts/jfr-profile.sh <PID> [duration_seconds]
# ============================================================

set -euo pipefail

PID=${1:-""}
DURATION=${2:-120}
OUTPUT_DIR="jfr-recordings"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RECORDING_FILE="${OUTPUT_DIR}/ratelimiter_${TIMESTAMP}.jfr"

mkdir -p "$OUTPUT_DIR"

if [ -z "$PID" ]; then
    echo "Discovering rate limiter process..."
    PID=$(jps -l | grep "RateLimiterApplication" | awk '{print $1}')
    if [ -z "$PID" ]; then
        echo "ERROR: RateLimiterApplication not found. Start the app first."
        echo "Usage: $0 [PID] [duration_seconds]"
        exit 1
    fi
    echo "Found process: PID=$PID"
fi

echo "============================================"
echo "JFR Profiling Configuration"
echo "============================================"
echo "  PID:        $PID"
echo "  Duration:   ${DURATION}s"
echo "  Output:     $RECORDING_FILE"
echo "============================================"

# Start JFR recording with detailed settings
jcmd "$PID" JFR.start \
    name=RateLimiterProfile \
    duration="${DURATION}s" \
    filename="$RECORDING_FILE" \
    settings=profile \
    maxsize=500m \
    dumponexit=true

echo ""
echo "Recording started. Run your load test now."
echo "Recording will auto-stop after ${DURATION}s."
echo ""
echo "To stop early:  jcmd $PID JFR.stop name=RateLimiterProfile"
echo "To dump now:    jcmd $PID JFR.dump name=RateLimiterProfile filename=$RECORDING_FILE"
echo ""

# Wait for recording to complete
sleep "$DURATION"

echo "Recording complete: $RECORDING_FILE"
echo ""
echo "=== Quick Analysis ==="

# Print summary if jfr CLI is available
if command -v jfr &> /dev/null; then
    echo ""
    echo "--- Top CPU Methods ---"
    jfr print --events jdk.ExecutionSample --stack-depth 5 "$RECORDING_FILE" | head -50

    echo ""
    echo "--- GC Pauses ---"
    jfr print --events jdk.GCPhasePause "$RECORDING_FILE" | head -20

    echo ""
    echo "--- Lock Contention ---"
    jfr print --events jdk.JavaMonitorWait "$RECORDING_FILE" | head -20

    echo ""
    echo "--- Object Allocation (Top) ---"
    jfr print --events jdk.ObjectAllocationInNewTLAB "$RECORDING_FILE" | head -30
else
    echo "Install JDK Mission Control for visual analysis:"
    echo "  Open JMC -> File -> Open -> $RECORDING_FILE"
fi

echo ""
echo "Full analysis: Open $RECORDING_FILE in JDK Mission Control (JMC)"
