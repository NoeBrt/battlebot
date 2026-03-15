import json
import uuid
import shutil
import numpy as np
import datetime
from pathlib import Path
import rl_train_vs_macduo as rl

def main():
    # 1. Load best parameters
    start_config_path = Path("best_to_start.json")
    if not start_config_path.exists():
        print(f"Error: {start_config_path} not found.")
        return

    print(f"Loading parameters from {start_config_path}...")
    with open(start_config_path) as f:
        data = json.load(f)

    if "paramsA" not in data:
        print("Error: paramsA not found in JSON.")
        return

    best_params_dict = data["paramsA"]

    # Reconstruct parameter array
    param_values = []
    for spec in rl.PARAM_SPEC:
        name = spec[0]
        if name not in best_params_dict:
            print(f"Warning: Parameter {name} missing, using default.")
            param_values.append(spec[1])
        else:
            param_values.append(best_params_dict[name])

    real_params = np.array(param_values)

    # 2. Setup environment
    worker_id = f"benchmark_{uuid.uuid4().hex[:8]}"
    print(f"Setting up benchmark worker: {worker_id}")
    worker_dir = rl.setup_worker(worker_id)
    
    try:
        # Generate code with best params
        rl.build_team_sources(worker_dir, team_pkg="rla", suffix="A", config_class="RLConfigA")
        rl.generate_rlconfig_java(
            real_params,
            worker_dir / "src" / "algorithms" / "rla" / "RLConfigA.java",
            "RLConfigA",
            "algorithms.rla",
        )
        
        # Configure matchup: Best RL vs MacDuo
        rl.patch_parameters_for_fixed_duel(worker_dir)
        
        # Compile
        print("Compiling...")
        if not rl.compile_java(worker_dir):
            print("Compilation failed!")
            return
            
        # 3. Run Benchmark
        n_matches = 10
        print(f"Running {n_matches} matches against MacDuo...")
        start_time = datetime.datetime.now()
        result = rl.run_match(worker_dir, n_matches=n_matches, timeout_ms=60000)
        duration = datetime.datetime.now() - start_time
        
        # 4. Generate Report
        report_dir = Path("benchmark/reports")
        report_dir.mkdir(parents=True, exist_ok=True)
        report_file = report_dir / f"benchmark_{start_time.strftime('%Y%m%d_%H%M%S')}.md"
        
        markdown_report = f"""# Benchmark Report: RL vs MacDuo

**Date:** {start_time.strftime('%Y-%m-%d %H:%M:%S')}
**Configuration:** Loaded from `best_to_start.json`
**Matches:** {n_matches}
**Duration:** {duration}

## Results
- **Score (RL):** {result['scoreA']:.4f}
- **Score (MacDuo):** {result['scoreB']:.4f}
- **Win Rate (RL):** {result['winRateA']:.2%}

### Average Stats per Match
| Metric | Team A (RL) | Team B (MacDuo) |
| :--- | :--- | :--- |
| **Dead Main** | {result['avgDeadMainA']:.2f} | {result['avgDeadMainB']:.2f} |
| **Dead Secondary** | {result['avgDeadSecA']:.2f} | {result['avgDeadSecB']:.2f} |
| **HP Remaining** | {result['avgHpA']:.2f} | {result['avgHpB']:.2f} |

## Detected Strategies
- **Team A Main:** `{result.get('strategyMainA', 'Unknown')}`
- **Team B Main:** `{result.get('strategyMainB', 'Unknown')}`

## Raw Result
```json
{json.dumps(result, indent=2)}
```
"""
        report_file.write_text(markdown_report)
        print(f"\nReport saved to: {report_file}")
        print("-" * 40)
        print(markdown_report)
        print("-" * 40)

    finally:
        # Cleanup
        if worker_dir.exists():
            shutil.rmtree(worker_dir)

if __name__ == "__main__":
    main()
