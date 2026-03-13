#!/usr/bin/env python3
"""
rl_train.py — Dual-population CMA-ES co-evolution trainer for Simovie Battlebot.
UPDATED: Uses [0,1] normalization for stable parameter evolution.
"""
import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

import numpy as np

# ──────────────────────────────────────────────────────────────────────────────
# PARAMETER SPACE DEFINITION
# ──────────────────────────────────────────────────────────────────────────────

# Each param: (name, default, low, high, is_int)
PARAM_SPEC = [
    # Targeting & scoring
    ("STALE_TTL",                  500,   120,   900,  True),
    ("TARGET_PROXIMITY_WEIGHT",    800.0, 200,  1500,  False),
    ("TARGET_TYPE_BONUS",          200.0,   0,   500,  False),
    ("TARGET_LOWHP_CRITICAL_BONUS",350.0,   0,   600,  False),
    ("TARGET_LOWHP_MODERATE_BONUS",180.0,   0,   400,  False),
    ("TARGET_FOCUS_BONUS",         400.0,   0,   800,  False),
    ("TARGET_SAFEFIRE_BONUS",      120.0,   0,   300,  False),
    ("TARGET_STALE_PENALTY",         5.0,   0,    20,  False),
    # Potential field
    ("PF_ENEMY_REPEL_STRENGTH",      2.5, 0.5,   5.0, False),
    ("PF_ENEMY_ATTRACT_STRENGTH",    0.6, 0.1,   2.0, False),
    ("PF_TANGENTIAL_STRENGTH",       0.7, 0.1,   2.0, False),
    ("PF_ALLY_REPEL_RANGE",        200.0,  50,   400,  False),
    ("PF_ALLY_REPEL_STRENGTH",       0.9, 0.1,   3.0, False),
    ("PF_WALL_STRENGTH",             0.6, 0.1,   2.0, False),
    ("PF_WRECK_RANGE",             150.0,  50,   300,  False),
    # Main bot kiting
    ("HOLD_X_OFFSET",            1300.0, 800,  1800,  False),
    ("KITE_MIN_AGGRO",            300.0, 150,   500,  False),
    ("KITE_MAX_AGGRO",            700.0, 400,   900,  False),
    ("KITE_MIN_NORMAL",           360.0, 200,   600,  False),
    ("KITE_MAX_NORMAL",           780.0, 500,  1000,  False),
    ("KITE_MIN_DEFEN",            420.0, 300,   700,  False),
    ("KITE_MAX_DEFEN",            900.0, 600,  1200,  False),
    ("HP_RETREAT_MAIN",            60.0,  20,   150,  False),
    ("NOFIRE_REPOSITION_TICKS",     20,   10,    50,  True),
    # Secondary bot
    ("HP_RETREAT_PCT_SEC",          0.35,0.15,  0.60, False),
    ("FLANK_Y_OFFSET",            300.0, 100,   500,  False),
    ("ADVANCE_X_A",              1750.0,1300,  2200,  False),
    ("PATROL_EVASION_RANGE",      400.0, 200,   600,  False),
]

N_PARAMS = len(PARAM_SPEC)
PARAM_NAMES   = [p[0] for p in PARAM_SPEC]
PARAM_DEFAULT = np.array([p[1] for p in PARAM_SPEC], dtype=float)
PARAM_LO      = np.array([p[2] for p in PARAM_SPEC], dtype=float)
PARAM_HI      = np.array([p[3] for p in PARAM_SPEC], dtype=float)
PARAM_IS_INT  = [p[4] for p in PARAM_SPEC]

RL_DIR = Path(__file__).resolve().parent

# ──────────────────────────────────────────────────────────────────────────────
# NORMALIZATION HELPERS
# ──────────────────────────────────────────────────────────────────────────────

def normalize(real_params):
    """Convert real parameter values to [0, 1] space."""
    return (real_params - PARAM_LO) / (PARAM_HI - PARAM_LO)

def denormalize(norm_params):
    """Convert [0, 1] space parameters to real values."""
    # Clamp inputs to [0,1] just in case
    n = np.clip(norm_params, 0.0, 1.0)
    return PARAM_LO + n * (PARAM_HI - PARAM_LO)

# ──────────────────────────────────────────────────────────────────────────────
# JAVA CODE GENERATION
# ──────────────────────────────────────────────────────────────────────────────

def generate_rlconfig_java(params: np.ndarray, filepath: Path, class_name: str = "RLConfig", package_name: str = "algorithms.rl"):
    """Generate a config java class with given REAL parameter values."""
    lines = [
        f"package {package_name};",
        "",
        f"public class {class_name} {{",
    ]
    for i, (name, default, lo, hi, is_int) in enumerate(PARAM_SPEC):
        val = params[i]
        val = max(lo, min(hi, val))  # double safety clamp
        if is_int:
            lines.append(f"    public static int    {name} = {int(round(val))};")
        else:
            lines.append(f"    public static double {name} = {val:.6f};")
    lines.append("}")
    lines.append("")
    filepath.write_text("\n".join(lines))


def build_team_sources(worker_dir: Path, team_pkg: str, suffix: str, config_class: str):
    """Create team-specific RL sources in algorithms.<team_pkg>."""
    src_root = worker_dir / "src" / "algorithms"
    team_dir = src_root / team_pkg
    team_dir.mkdir(parents=True, exist_ok=True)

    base_src = (src_root / "rl" / "RLBotBase.java").read_text()
    main_src = (src_root / "rl" / "RLBotMain.java").read_text()
    sec_src = (src_root / "rl" / "RLBotSecondary.java").read_text()

    def transform(src: str, old_cls: str, new_cls: str):
        src = src.replace("package algorithms.rl;", f"package algorithms.{team_pkg};")
        src = src.replace("RLConfig", config_class)
        src = src.replace(old_cls, new_cls)
        return src

    base_out = transform(base_src, "RLBotBase", f"RLBotBase{suffix}")
    main_out = transform(main_src, "RLBotMain", f"RLBotMain{suffix}")
    sec_out = transform(sec_src, "RLBotSecondary", f"RLBotSecondary{suffix}")

    main_out = main_out.replace("extends RLBotBase", f"extends RLBotBase{suffix}")
    sec_out = sec_out.replace("extends RLBotBase", f"extends RLBotBase{suffix}")

    (team_dir / f"RLBotBase{suffix}.java").write_text(base_out)
    (team_dir / f"RLBotMain{suffix}.java").write_text(main_out)
    (team_dir / f"RLBotSecondary{suffix}.java").write_text(sec_out)


def patch_parameters_for_duel(worker_dir: Path):
    """Set Parameters.java to use distinct RL teams on both sides."""
    p = worker_dir / "src" / "characteristics" / "Parameters.java"
    txt = p.read_text()
    txt = re.sub(
        r'public static final String teamAMainBotBrainClassName = ".*?";',
        'public static final String teamAMainBotBrainClassName = "algorithms.rla.RLBotMainA";',
        txt,
    )
    txt = re.sub(
        r'public static final String teamASecondaryBotBrainClassName = ".*?";',
        'public static final String teamASecondaryBotBrainClassName = "algorithms.rla.RLBotSecondaryA";',
        txt,
    )
    txt = re.sub(
        r'public static final String teamBMainBotBrainClassName = ".*?";',
        'public static final String teamBMainBotBrainClassName = "algorithms.rlb.RLBotMainB";',
        txt,
    )
    txt = re.sub(
        r'public static final String teamBSecondaryBotBrainClassName = ".*?";',
        'public static final String teamBSecondaryBotBrainClassName = "algorithms.rlb.RLBotSecondaryB";',
        txt,
    )
    p.write_text(txt)


def compile_java(work_dir: Path) -> bool:
    """Compile all Java sources in work_dir."""
    src_dir = work_dir / "src"
    beans_dir = work_dir / "beans"
    jars_dir = work_dir / "jars"

    if beans_dir.exists():
        shutil.rmtree(beans_dir)
    beans_dir.mkdir()

    sources = list(src_dir.rglob("*.java"))
    sources_file = work_dir / "sources.txt"
    sources_file.write_text("\n".join(str(s) for s in sources))

    result = subprocess.run(
        ["javac", "-cp", f"{jars_dir}/*", "-d", str(beans_dir), "--release", "11",
         f"@{sources_file}"],
        capture_output=True, text=True, cwd=str(work_dir), timeout=30
    )
    sources_file.unlink(missing_ok=True)

    if result.returncode != 0:
        print(f"  [COMPILE ERROR] {result.stderr[:500]}")
        return False
    return True

# ──────────────────────────────────────────────────────────────────────────────
# MATCH EXECUTION
# ──────────────────────────────────────────────────────────────────────────────

def run_match(work_dir: Path, n_matches: int = 3, timeout_ms: int = 30000,
              delay_ms: int = 1) -> dict:
    """Run headless matches and return parsed results."""
    jars = work_dir / "jars"
    beans = work_dir / "beans"
    log_dir = work_dir / "rl_logs"
    log_dir.mkdir(exist_ok=True)

    cmd = [
        "java", "-cp", f"{jars}/*:{beans}",
        "supportGUI.HeadlessMatchRunner",
        str(n_matches), str(timeout_ms), str(delay_ms), str(log_dir)
    ]

    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True,
            cwd=str(work_dir), timeout=(timeout_ms / 1000 * n_matches + 60)
        )
        output = result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        return {"scoreA": 0.0, "scoreB": 0.0, "winRateA": 0.0, "error": "timeout"}

    return parse_match_output(output)


def parse_match_output(output: str) -> dict:
    """Parse SUMMARY block from HeadlessMatchRunner output."""
    result = {
        "scoreA": 0.0, "scoreB": 0.0, "winRateA": 0.5,
        "avgFireCountA": 0.0,
        "avgDeadMainA": 0.0, "avgDeadSecA": 0.0,
        "avgDeadMainB": 0.0, "avgDeadSecB": 0.0,
        "avgDmgMainByA": 0.0, "avgDmgSecByA": 0.0,
        "avgDmgMainByB": 0.0, "avgDmgSecByB": 0.0,
        "error": None,
    }
    m = re.search(r"avgScoreA=([\d.]+)\s+avgScoreB=([\d.]+)", output)
    if m: result["scoreA"], result["scoreB"] = float(m.group(1)), float(m.group(2))
    
    m = re.search(r"winRateA=([\d.]+)", output)
    if m: result["winRateA"] = float(m.group(1))

    m = re.search(r"avgFireCountA=([\d.]+)", output)
    if m: result["avgFireCountA"] = float(m.group(1))

    m = re.search(r"avgDeadMainA=([\d.]+)\s+avgDeadSecA=([\d.]+)", output)
    if m: result["avgDeadMainA"], result["avgDeadSecA"] = float(m.group(1)), float(m.group(2))

    m = re.search(r"avgDeadMainB=([\d.]+)\s+avgDeadSecB=([\d.]+)", output)
    if m: result["avgDeadMainB"], result["avgDeadSecB"] = float(m.group(1)), float(m.group(2))

    m = re.search(r"avgDmgMainByA=([\d.]+)\s+avgDmgSecByA=([\d.]+)", output)
    if m: result["avgDmgMainByA"], result["avgDmgSecByA"] = float(m.group(1)), float(m.group(2))

    m = re.search(r"avgDmgMainByB=([\d.]+)\s+avgDmgSecByB=([\d.]+)", output)
    if m: result["avgDmgMainByB"], result["avgDmgSecByB"] = float(m.group(1)), float(m.group(2))

    if not re.search(r"SUMMARY", output):
        result["error"] = "no_summary"
    return result


def setup_worker(worker_id: int) -> Path:
    worker_dir = RL_DIR / "rl_workers" / f"worker_{worker_id}"
    worker_dir.mkdir(parents=True, exist_ok=True)
    for name in ["jars", "avatars", "META-INF"]:
        link = worker_dir / name
        target = RL_DIR / name
        if not link.exists() and target.exists():
            link.symlink_to(target)
    src_dst = worker_dir / "src"
    if src_dst.exists():
        shutil.rmtree(src_dst)
    shutil.copytree(RL_DIR / "src", src_dst)
    return worker_dir


def evaluate_matchup(args):
    """Evaluate one A vs B matchup. Params passed here are NORMALIZED [0,1]."""
    worker_id, norm_a, norm_b, n_matches, timeout_ms = args
    worker_dir = setup_worker(worker_id)
    
    # Denormalize to real values for Java config
    real_a = denormalize(norm_a)
    real_b = denormalize(norm_b)

    build_team_sources(worker_dir, team_pkg="rla", suffix="A", config_class="RLConfigA")
    build_team_sources(worker_dir, team_pkg="rlb", suffix="B", config_class="RLConfigB")
    
    generate_rlconfig_java(real_a, worker_dir / "src" / "algorithms" / "rla" / "RLConfigA.java", "RLConfigA", "algorithms.rla")
    generate_rlconfig_java(real_b, worker_dir / "src" / "algorithms" / "rlb" / "RLConfigB.java", "RLConfigB", "algorithms.rlb")
    
    patch_parameters_for_duel(worker_dir)

    if not compile_java(worker_dir):
        return worker_id, -1.0, -1.0

    result = run_match(worker_dir, n_matches=n_matches, timeout_ms=timeout_ms)
    if result["error"]:
        return worker_id, -0.5, -0.5

    # Fitness: Damage + Kills
    dmg_a = 0.75 * result["avgDmgMainByA"] + 0.25 * result["avgDmgSecByA"]
    dmg_b = 0.75 * result["avgDmgMainByB"] + 0.25 * result["avgDmgSecByB"]

    kill_bonus_a = 0.35 * (result["avgDeadMainB"] / 3.0) + 0.15 * (result["avgDeadSecB"] / 2.0)
    kill_bonus_b = 0.35 * (result["avgDeadMainA"] / 3.0) + 0.15 * (result["avgDeadSecA"] / 2.0)

    return worker_id, dmg_a + kill_bonus_a, dmg_b + kill_bonus_b


# ──────────────────────────────────────────────────────────────────────────────
# CMA-ES IMPLEMENTATION (Normalized)
# ──────────────────────────────────────────────────────────────────────────────

class CMAES:
    def __init__(self, x0: np.ndarray, sigma0: float = 0.2, pop_size: int = None):
        """x0 and sigma0 are in normalized space [0,1]."""
        self.n = len(x0)
        self.mean = x0.copy()  # Kept in [0,1]
        self.sigma = sigma0

        self.lam = pop_size or (4 + int(3 * math.log(self.n)))
        self.mu = self.lam // 2
        
        weights = np.log(self.mu + 0.5) - np.log(np.arange(1, self.mu + 1))
        self.weights = weights / weights.sum()
        self.mueff = 1.0 / (self.weights ** 2).sum()
        self.cs = (self.mueff + 2) / (self.n + self.mueff + 5)
        self.ds = 1 + 2 * max(0, math.sqrt((self.mueff - 1) / (self.n + 1)) - 1) + self.cs
        self.chiN = math.sqrt(self.n) * (1 - 1 / (4 * self.n) + 1 / (21 * self.n ** 2))
        self.cc = (4 + self.mueff / self.n) / (self.n + 4 + 2 * self.mueff / self.n)
        self.c1 = 2 / ((self.n + 1.3) ** 2 + self.mueff)
        self.cmu = min(1 - self.c1, 2 * (self.mueff - 2 + 1 / self.mueff) / ((self.n + 2) ** 2 + self.mueff))

        self.ps = np.zeros(self.n)
        self.pc = np.zeros(self.n)
        self.C = np.eye(self.n)
        self.invsqrtC = np.eye(self.n)
        self.eigeneval = 0
        self.gen = 0

    def ask(self) -> list:
        solutions = []
        for _ in range(self.lam):
            z = np.random.randn(self.n)
            x = self.mean + self.sigma * (self.invsqrtC @ z)
            x = np.clip(x, 0.0, 1.0) # Always clamp to normalized bounds
            solutions.append(x)
        return solutions

    def tell(self, solutions: list, fitnesses: list):
        self.gen += 1
        idx = np.argsort(fitnesses)[::-1]
        old_mean = self.mean.copy()
        self.mean = np.zeros(self.n)
        for i in range(self.mu):
            self.mean += self.weights[i] * solutions[idx[i]]
        
        y = (self.mean - old_mean) / self.sigma
        z = self.invsqrtC @ y
        self.ps = (1 - self.cs) * self.ps + math.sqrt(self.cs * (2 - self.cs) * self.mueff) * z
        hsig = (np.linalg.norm(self.ps) / math.sqrt(1 - (1 - self.cs) ** (2 * self.gen)) / self.chiN < 1.4 + 2 / (self.n + 1))
        self.pc = ((1 - self.cc) * self.pc + hsig * math.sqrt(self.cc * (2 - self.cc) * self.mueff) * y)
        
        artmp = np.zeros((self.n, self.mu))
        for i in range(self.mu):
            artmp[:, i] = (solutions[idx[i]] - old_mean) / self.sigma
        
        self.C = ((1 - self.c1 - self.cmu) * self.C + self.c1 * (np.outer(self.pc, self.pc) + (1 - hsig) * self.cc * (2 - self.cc) * self.C) + self.cmu * artmp @ np.diag(self.weights) @ artmp.T)
        self.sigma *= math.exp((self.cs / self.ds) * (np.linalg.norm(self.ps) / self.chiN - 1))
        
        if self.gen - self.eigeneval > self.lam / (self.c1 + self.cmu) / self.n / 10:
            self.eigeneval = self.gen
            self.C = np.triu(self.C) + np.triu(self.C, 1).T
            D, B = np.linalg.eigh(self.C)
            D = np.maximum(D, 1e-20)
            self.invsqrtC = B @ np.diag(1.0 / np.sqrt(D)) @ B.T

    @property
    def best_params(self):
        # Return REAL values for external usage/saving
        return denormalize(self.mean)


# ──────────────────────────────────────────────────────────────────────────────
# TRAINING LOOP
# ──────────────────────────────────────────────────────────────────────────────

def train(args):
    print("=" * 70)
    print("  CMA-ES Evolutionary Self-Play Trainer for Simovie Battlebot")
    print("  UPDATE: Normalized Param Space [0,1]")
    print("=" * 70)
    
    results_dir = RL_DIR / "rl_results"
    results_dir.mkdir(exist_ok=True)
    run_id = time.strftime("%Y%m%d_%H%M%S")
    run_dir = results_dir / f"run_{run_id}"
    run_dir.mkdir()

    # Initialize in NORMALIZED space
    # Default sigma=0.2 means 20% of range mutation step
    initial_sigma = 0.2  
    start_params_a = normalize(PARAM_DEFAULT)
    start_params_b = normalize(PARAM_DEFAULT)

    # Note: If resuming, need to load real params and normalize them
    # (Simplified: ignoring resume logic specific handling for now, assuming fresh start 
    #  or you can manually fix resume if needed)

    cma_a = CMAES(start_params_a, sigma0=initial_sigma, pop_size=args.pop)
    cma_b = CMAES(start_params_b, sigma0=initial_sigma, pop_size=args.pop)

    best_fitness_a = -float("inf")
    best_fitness_b = -float("inf")
    # Store REAL params for best
    best_params_a = denormalize(start_params_a)
    best_params_b = denormalize(start_params_b)
    history = []

    for gen in range(args.generations):
        t0 = time.time()
        print(f"\n--- Generation {gen + 1}/{args.generations} ---")

        # Ask returns NORMALIZED candidates
        cand_a_norm = cma_a.ask()
        cand_b_norm = cma_b.ask()

        eval_args = [
            (i % args.workers, cand_a_norm[i], cand_b_norm[i], args.matches, args.timeout)
            for i in range(len(cand_a_norm))
        ]

        fitnesses_a = [0.0] * len(cand_a_norm)
        fitnesses_b = [0.0] * len(cand_b_norm)

        batch_size = args.workers
        for batch_start in range(0, len(eval_args), batch_size):
            batch = eval_args[batch_start:batch_start + batch_size]
            with ProcessPoolExecutor(max_workers=min(len(batch), args.workers)) as executor:
                futures = {executor.submit(evaluate_matchup, a): (batch_start + j) for j, a in enumerate(batch)}
                for future in as_completed(futures):
                    idx = futures[future]
                    try:
                        worker_id, fit_a, fit_b = future.result()
                        fitnesses_a[idx] = fit_a
                        fitnesses_b[idx] = fit_b
                    except Exception as e:
                        print(f"  [ERROR] Candidate {idx}: {e}")
                        fitnesses_a[idx] = -1.0; fitnesses_b[idx] = -1.0

        cma_a.tell(cand_a_norm, fitnesses_a)
        cma_b.tell(cand_b_norm, fitnesses_b)

        # Track best (fitness)
        gen_best_idx_a = int(np.argmax(fitnesses_a))
        gen_best_idx_b = int(np.argmax(fitnesses_b))
        
        if fitnesses_a[gen_best_idx_a] > best_fitness_a:
            best_fitness_a = fitnesses_a[gen_best_idx_a]
            # Convert to REAL for storage
            best_params_a = denormalize(cand_a_norm[gen_best_idx_a])

        if fitnesses_b[gen_best_idx_b] > best_fitness_b:
            best_fitness_b = fitnesses_b[gen_best_idx_b]
            best_params_b = denormalize(cand_b_norm[gen_best_idx_b])

        elapsed = time.time() - t0
        print(f"  Team A best: {fitnesses_a[gen_best_idx_a]:.4f}  Sigma: {cma_a.sigma:.4f}")
        print(f"  Team B best: {fitnesses_b[gen_best_idx_b]:.4f}  Sigma: {cma_b.sigma:.4f}")
        print(f"  Global best: A={best_fitness_a:.4f} B={best_fitness_b:.4f}")

        # Save Checkpoint (using real params)
        if (gen + 1) % 5 == 0 or gen == args.generations - 1:
            save_checkpoint(run_dir, gen + 1, best_params_a, best_fitness_a, best_params_b, best_fitness_b, history)

    print("\nTRAINING COMPLETE")
    final_a = RL_DIR / "src" / "algorithms" / "rl" / "RLConfig_best_A.java"
    final_b = RL_DIR / "src" / "algorithms" / "rl" / "RLConfig_best_B.java"
    generate_rlconfig_java(best_params_a, final_a, "RLConfig_best_A", "algorithms.rl")
    generate_rlconfig_java(best_params_b, final_b, "RLConfig_best_B", "algorithms.rl")
    export_best_policies(run_dir, best_params_a, best_fitness_a, best_params_b, best_fitness_b)


def save_checkpoint(run_dir, gen, params_a, fitness_a, params_b, fitness_b, history):
    # (Same as before but params are real)
    checkpoint = {
        "generation": gen,
        "best_fitness_A": float(fitness_a),
        "best_fitness_B": float(fitness_b),
        "paramsA": {PARAM_NAMES[i]: float(params_a[i]) for i in range(N_PARAMS)},
        "paramsB": {PARAM_NAMES[i]: float(params_b[i]) for i in range(N_PARAMS)},
    }
    (run_dir / f"checkpoint_gen{gen:04d}.json").write_text(json.dumps(checkpoint, indent=2))
    (run_dir / "latest.json").write_text(json.dumps(checkpoint, indent=2))
    generate_rlconfig_java(params_a, run_dir / f"RLConfigA_gen{gen:04d}.java", f"RLConfigA_gen{gen:04d}", "algorithms.rl")
    generate_rlconfig_java(params_b, run_dir / f"RLConfigB_gen{gen:04d}.java", f"RLConfigB_gen{gen:04d}", "algorithms.rl")

def export_best_policies(run_dir, params_a, fitness_a, params_b, fitness_b):
    # (Same as before)
    best_dir = run_dir / "best_policy"
    best_dir.mkdir(exist_ok=True)
    payload_a = {"team": "A", "best_fitness": float(fitness_a), "params": {PARAM_NAMES[i]: float(params_a[i]) for i in range(N_PARAMS)}}
    payload_b = {"team": "B", "best_fitness": float(fitness_b), "params": {PARAM_NAMES[i]: float(params_b[i]) for i in range(N_PARAMS)}}
    (best_dir / "teamA_best_policy.json").write_text(json.dumps(payload_a, indent=2))
    (best_dir / "teamB_best_policy.json").write_text(json.dumps(payload_b, indent=2))
    generate_rlconfig_java(params_a, best_dir / "RLConfig_best_A.java", "RLConfig_best_A", "algorithms.rl")
    generate_rlconfig_java(params_b, best_dir / "RLConfig_best_B.java", "RLConfig_best_B", "algorithms.rl")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--generations", type=int, default=50)
    parser.add_argument("--pop", type=int, default=14)
    parser.add_argument("--matches", type=int, default=3)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--timeout", type=int, default=30000)
    parser.add_argument("--resume-a", type=str, default=None)
    parser.add_argument("--resume-b", type=str, default=None)
    args = parser.parse_args()
    train(args)
