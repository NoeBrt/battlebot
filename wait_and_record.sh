#!/bin/bash
echo "Waiting for Python training loop to finish..."
while pgrep -f "python3 rl_train_fixed" > /dev/null; do
    sleep 30
done

echo "Training complete! Compiling final code..."
./compile_rl.sh

LATEST_RUN=$(ls -td rl_results/run_* | head -1)
BEST_JSON="${LATEST_RUN}/best_policy.json"

if [ -f "$BEST_JSON" ]; then
    echo "Using generated parameters: $BEST_JSON"
    ./headless_launcher.sh record output_final.mp4 "$BEST_JSON"
else
    echo "Warning: best_policy.json not found in $LATEST_RUN. Using fallback."
    # Wait, the script also creates JSON files per generation! `checkpoint_gen0050.json`
    CHECKPOINT_JSON="${LATEST_RUN}/checkpoint_gen0050.json"
    if [ -f "$CHECKPOINT_JSON" ]; then
        ./headless_launcher.sh record output_final.mp4 "$CHECKPOINT_JSON"
    else
        ./headless_launcher.sh record output_final.mp4
    fi
fi
echo "Done!"
