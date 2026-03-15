# Simovie Battlebot - RL Implementation

This repository contains a heavily optimized reinforcement learning framework and bot behaviors for defeating the `MacDuo` baseline teams in the Simovie Battlebot simulator.

## Features
- **RLBotMain / RLBotSecondary logic:** Improved logic including sticky aggro mechanics, dynamic reverse driving, safe-fire radius raycasting to prevent friendly fire, and refined movement fields.
- **CMA-ES Python Trainer:** High-performance headless trainer for simulating and evolving continuous bot parameter weights (`rl_train_fixed_improved.py`).
- **Parallel Headless Matches:** Headless Java wrapper to process simulations up to 100x faster than graphical speed limitations without dropping GUI integration requirements (via `xvfb-run` virtualization).

## Execution

### 1. Requirements
- **Java 11+** for the main engine runtime.
- **Python 3.12+** and `numpy` for training operations.
- **xvfb & ffmpeg** for graphical rendering/headless recording (Linux).

### 2. Standard Match (GUI/Headless)
To compile and launch a simulation immediately:
```bash
# General quick setup
./compile_rl.sh
# Syntax: ./headless_launcher.sh [matches] [timeout_ms] [delay_ms] [logs_folder]
./headless_launcher.sh 5 30000 1 logs
```

### 3. Training
To start optimizing your own behaviors with the bundled CMA-ES model:
```bash
python3 -m venv .venv
source .venv/bin/activate
# Requires numpy installed
python3 rl_train_fixed_improved.py --workers 10 --generations 50 --pop 20 --matches 5
```

### 4. Headless Video Recording
To generate a `.mp4` video from a trained headless engine:
```bash
# Syntax: ./headless_launcher.sh record [output_name.mp4] [optional_best_params.json]
./headless_launcher.sh record output_match.mp4
```

## RL Parameters (Export)
You can find the extracted ultimate `RLBot` generation parameters within:
`exported_rl_bot.tar.gz` 
This archive is ready to be directly mapped over `algorithms.rl` as a standalone package!
