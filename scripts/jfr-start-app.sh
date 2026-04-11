#!/bin/bash
# ============================================================
# Start the Rate Limiter application with JFR enabled from boot.
#
# This captures the FULL lifecycle including startup, warm-up,
# and load testing phases. The recording runs continuously and
# dumps on exit.
#
# Usage:
#   ./scripts/jfr-start-app.sh
#   (Run your k6 tests against the running app)
#   (Press Ctrl+C to stop — recording is saved automatically)
# ============================================================

set -euo pipefail

OUTPUT_DIR="jfr-recordings"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RECORDING_FILE="${OUTPUT_DIR}/ratelimiter_full_${TIMESTAMP}.jfr"

mkdir -p "$OUTPUT_DIR"

echo "============================================"
echo "Starting Rate Limiter with JFR Profiling"
echo "============================================"
echo "  Recording: $RECORDING_FILE"
echo "  Settings:  profile (CPU + memory + locks + GC)"
echo "============================================"
echo ""

# JFR flags explained:
#   StartFlightRecording: begins recording at JVM startup
#   settings=profile: detailed profiling (CPU, memory, locks, I/O)
#   dumponexit=true: saves recording when JVM shuts down
#   maxsize=1g: circular buffer capped at 1GB
#   maxage=30m: keep last 30 minutes of data
#
# Additional JVM tuning for accurate profiling:
#   -XX:+UnlockDiagnosticVMOptions: enables diagnostic flags
#   -XX:+DebugNonSafepoints: improves stack trace accuracy in JFR
#   -Xlog:gc*: logs GC events to correlate with JFR data

mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
  -XX:StartFlightRecording=name=FullProfile,settings=profile,dumponexit=true,filename=$RECORDING_FILE,maxsize=1g,maxage=30m \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+DebugNonSafepoints \
  -XX:FlightRecorderOptions=stackdepth=128 \
  -Xms512m \
  -Xmx512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=10 \
  -Xlog:gc*:file=jfr-recordings/gc_${TIMESTAMP}.log:time,level,tags:filecount=5,filesize=10m"
