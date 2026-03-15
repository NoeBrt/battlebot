#!/bin/bash
set -euo pipefail

# Usage:
#   ./headless_launcher.sh [num_matches] [timeout_ms] [delay_ms] [logdir]
#   ./headless_launcher.sh record [output.mp4]

compile() {
    echo "Compiling sources..."
    rm -rf beans
    mkdir -p beans
    find src -name "*.java" > sources.txt
    javac -cp "jars/*" -d beans --release 11 @sources.txt
    rm sources.txt
}

run_headless() {
    LOGDIR="${4:-logs}"
    mkdir -p "$LOGDIR"
    
    # Defaults in case not provided
    MATCHES="${1:-5}"
    TIMEOUT="${2:-90000}" 
    DELAY="${3:-1}"

    CMD="java -cp jars/*:beans supportGUI.HeadlessMatchRunner $MATCHES $TIMEOUT $DELAY $LOGDIR"
    
    if command -v xvfb-run &> /dev/null; then
        echo "Using xvfb-run for headless display..."
        xvfb-run -a $CMD
    else
        echo "Warning: xvfb-run not found. GUI creation may fail if no display is available."
        $CMD
    fi
}

run_record() {
    OUTPUT="${2:-match.mp4}"
    echo "Recording match to $OUTPUT..."

    # Check for ffmpeg
    if ! command -v ffmpeg &> /dev/null; then
        echo "Error: ffmpeg is required for recording."
        exit 1
    fi

    # Command to run inside Xvfb:
    # 1. Start ffmpeg on the current DISPLAY (grabbed from environment inside xvfb-run)
    # 2. Start AutoRecordingViewer
    # 3. Kill ffmpeg when java exits
    SCRIPT="ffmpeg -y -nostdin -f x11grab -draw_mouse 0 -framerate 30 -video_size 1280x720 -i :\${DISPLAY##*:} -c:v libx264 -pix_fmt yuv420p -preset ultrafast $OUTPUT & PID=\$!; sleep 2; java -cp jars/*:beans supportGUI.AutoRecordingViewer; kill \$PID"
    
    xvfb-run -a -s "-screen 0 1280x720x24" bash -c "$SCRIPT"
    
    echo "Recording saved to $OUTPUT"
}

# Main logic
if [ "${1:-}" != "" ] && [ -f "${1:-}" ] && [[ "${1:-}" == *.json ]]; then
    echo "Found config file: ${1}"
    python3 apply_config.py "${1}" src/algorithms/rl/RLConfig.java
    shift # Remove the config file from arguments
fi

MODE="${1:-headless}"

if [ "$MODE" == "record" ]; then
    compile
    run_record "$@"
else
    # Assume default headless mode arguments
    compile
    run_headless "$@"
fi
