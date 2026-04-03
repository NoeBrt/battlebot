#!/bin/bash
set -euo pipefail

# Usage: ./headless_launcher.sh [num_matches] [timeout_ms] [delay_ms] [logdir]
# Example: ./headless_launcher.sh 5 90000 1
# Example: ./headless_launcher.sh 10 120000 1 logs/experiment1

LOGDIR="${4:-logs}"
mkdir -p "$LOGDIR"

# Clean prior compilation
rm -rf beans
mkdir -p beans

# Find all internal source files
find src -name "*.java" > sources.txt

echo "Compiling sources..."
# Compile with backwards compatibility for Java 11 (fixes class version 68.0 errors)
javac -cp "jars/*" -d beans @sources.txt

# Remove temporary file list
rm sources.txt

echo "Running Headless Match (caffeinate: system will stay awake)..."
# caffeinate -dims prevents: display sleep (-d), idle sleep (-i), disk sleep (-m), system sleep (-s)
# This ensures the match keeps running even if the lid is closed or the screen locks.
# Arguments forwarded: [num_matches] [timeout_ms] [delay_ms] [logdir]
caffeinate -dims java -cp "jars/*:beans" supportGUI.HeadlessMatchRunner "${1:-5}" "${2:-90000}" "${3:-1}" "$LOGDIR"
