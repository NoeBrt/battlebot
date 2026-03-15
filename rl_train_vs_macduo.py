#!/usr/bin/env python3
"""
rl_train_fixed.py

Mono-population CMA-ES trainer for Simovie Battlebot against a fixed opponent
(MacDuo).

Corrections applied:
- true fixed-opponent mode, removed fake co-evolution population B
- fixed CMA-ES sampling: sample with sqrtC, whiten with invsqrtC
- one ProcessPoolExecutor per generation
- simplified checkpoints and exports
- cleaner worker setup / evaluation loop
- kept [0,1] normalized search space
"""

import argparse
import json
import math
import os
import re
import shutil
import subprocess
import time
import uuid
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

import numpy as np

# -----------------------------------------------------------------------------
# PARAMETER SPACE
# -----------------------------------------------------------------------------

# Each param: (name, default, low, high, is_int)
PARAM_SPEC = [
    ("STALE_TTL",                   500,   120,   900,  True),
    ("TARGET_PROXIMITY_WEIGHT",     800.0, 200,  1500,  False),
    ("TARGET_TYPE_BONUS",           200.0,   0,   500,  False),
    ("TARGET_LOWHP_CRITICAL_BONUS", 350.0,   0,   600,  False),
    ("TARGET_LOWHP_MODERATE_BONUS", 180.0,   0,   400,  False),
    ("TARGET_FOCUS_BONUS",          400.0,   0,   800,  False),
    ("TARGET_SAFEFIRE_BONUS",       120.0,   0,   300,  False),
    ("TARGET_STALE_PENALTY",          5.0,   0,    20,  False),

    ("PF_ENEMY_REPEL_STRENGTH",       2.5, 0.5,   5.0, False),
    ("PF_ENEMY_ATTRACT_STRENGTH",     0.6, 0.1,   2.0, False),
    ("PF_TANGENTIAL_STRENGTH",        0.7, 0.1,   2.0, False),
    ("PF_ALLY_REPEL_RANGE",         200.0,  50,   400, False),
    ("PF_ALLY_REPEL_STRENGTH",        0.9, 0.1,   3.0, False),
    ("PF_WALL_STRENGTH",              0.6, 0.1,   2.0, False),
    ("PF_WRECK_RANGE",              150.0,  50,   300, False),

    ("HOLD_X_OFFSET",              1300.0, 800,  1800, False),
    ("KITE_MIN_AGGRO",              300.0, 150,   500, False),
    ("KITE_MAX_AGGRO",              700.0, 400,   900, False),
    ("KITE_MIN_NORMAL",             360.0, 200,   600, False),
    ("KITE_MAX_NORMAL",             780.0, 500,  1000, False),
    ("KITE_MIN_DEFEN",              420.0, 300,   700, False),
    ("KITE_MAX_DEFEN",              900.0, 600,  1200, False),
    ("HP_RETREAT_MAIN",              60.0,  20,   150, False),
    ("NOFIRE_REPOSITION_TICKS",       20,   10,    50, True),

    ("HP_RETREAT_PCT_SEC",           0.35, 0.15,  0.60, False),
    ("FLANK_Y_OFFSET",             300.0, 100,   500, False),
    ("ADVANCE_X_A",               1750.0,1300,  2200, False),
    ("PATROL_EVASION_RANGE",       400.0, 200,   600, False),
]

N_PARAMS = len(PARAM_SPEC)
PARAM_NAMES   = [p[0] for p in PARAM_SPEC]
PARAM_DEFAULT = np.array([p[1] for p in PARAM_SPEC], dtype=float)
PARAM_LO      = np.array([p[2] for p in PARAM_SPEC], dtype=float)
PARAM_HI      = np.array([p[3] for p in PARAM_SPEC], dtype=float)
PARAM_IS_INT  = [p[4] for p in PARAM_SPEC]

FIXED_CONSTANTS = {
    "MAP_WIDTH": 3000.0,
    "MAP_HEIGHT": 2000.0,
    "MAP_CX": 1500.0,
    "MAP_CY": 1000.0,
    "FORMATION_Y_BASE": 1000.0,
    "FORMATION_Y_OFFSET": 260.0,
    "WALL_MARGIN": 200.0,
    "SAFE_ZONE_X": 200.0,
    "FLANK_OFFSET": 150.0,
    "HEALTH_HIGH_THRESHOLD": 200.0,
    "HEALTH_LOW_THRESHOLD": 100.0,
    "PATROL_THRESHOLD": 200.0,
    "FIRING_ANGLE_TOLERANCE": 0.2,
    "MAX_HEALTH_MAIN": 300.0,
    "MAX_HEALTH_SEC": 100.0,
}

RL_DIR = Path(__file__).resolve().parent

SIGMA_MIN = 0.02
SIGMA_RESTART = 0.15

# If your runner metrics really correspond to 5 bots per team, keep 5.
# Otherwise change this constant once after checking HeadlessMatchRunner.
TEAM_SIZE = 5.0


# -----------------------------------------------------------------------------
# NORMALIZATION
# -----------------------------------------------------------------------------

def normalize(real_params: np.ndarray) -> np.ndarray:
    return (real_params - PARAM_LO) / (PARAM_HI - PARAM_LO)


def denormalize(norm_params: np.ndarray) -> np.ndarray:
    n = np.clip(norm_params, 0.0, 1.0)
    return PARAM_LO + n * (PARAM_HI - PARAM_LO)


# -----------------------------------------------------------------------------
# JAVA CODE GENERATION
# -----------------------------------------------------------------------------

def generate_rlconfig_java(
    params: np.ndarray,
    filepath: Path,
    class_name: str = "RLConfig",
    package_name: str = "algorithms.rl",
):
    lines = [
        f"package {package_name};",
        "",
        f"public class {class_name} {{",
    ]

    for i, (name, _default, lo, hi, is_int) in enumerate(PARAM_SPEC):
        val = float(np.clip(params[i], lo, hi))
        if is_int:
            lines.append(f"    public static final int    {name} = {int(round(val))};")
        else:
            lines.append(f"    public static final double {name} = {val:.6f};")

    lines.append("")
    for name, val in FIXED_CONSTANTS.items():
        if isinstance(val, int):
            lines.append(f"    public static final int    {name} = {val};")
        else:
            lines.append(f"    public static final double {name} = {float(val):.6f};")

    lines.append("}")
    lines.append("")
    filepath.write_text("\n".join(lines))


def build_team_sources(worker_dir: Path, team_pkg: str, suffix: str, config_class: str):
    src_root = worker_dir / "src" / "algorithms"
    team_dir = src_root / team_pkg
    team_dir.mkdir(parents=True, exist_ok=True)

    base_src = (src_root / "rl" / "RLBotBase.java").read_text()
    main_src = (src_root / "rl" / "RLBotMain.java").read_text()
    sec_src  = (src_root / "rl" / "RLBotSecondary.java").read_text()

    def transform(src: str, old_cls: str, new_cls: str) -> str:
        src = src.replace("package algorithms.rl;", f"package algorithms.{team_pkg};")
        src = src.replace("RLConfig", config_class)
        src = src.replace(old_cls, new_cls)
        return src

    base_out = transform(base_src, "RLBotBase", f"RLBotBase{suffix}")
    main_out = transform(main_src, "RLBotMain", f"RLBotMain{suffix}")
    sec_out  = transform(sec_src,  "RLBotSecondary", f"RLBotSecondary{suffix}")

    main_out = main_out.replace("extends RLBotBase", f"extends RLBotBase{suffix}")
    sec_out  = sec_out.replace("extends RLBotBase",  f"extends RLBotBase{suffix}")

    (team_dir / f"RLBotBase{suffix}.java").write_text(base_out)
    (team_dir / f"RLBotMain{suffix}.java").write_text(main_out)
    (team_dir / f"RLBotSecondary{suffix}.java").write_text(sec_out)


def patch_parameters_for_fixed_duel(worker_dir: Path):
    """
    Team A uses the evolved RL bots.
    Team B uses fixed MacDuo.
    """
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
        'public static final String teamBMainBotBrainClassName = "algorithms.external.MacDuoMain";',
        txt,
    )
    txt = re.sub(
        r'public static final String teamBSecondaryBotBrainClassName = ".*?";',
        'public static final String teamBSecondaryBotBrainClassName = "algorithms.external.MacDuoSecondary";',
        txt,
    )

    p.write_text(txt)


def compile_java(work_dir: Path) -> bool:
    src_dir = work_dir / "src"
    beans_dir = work_dir / "beans"
    jars_dir = work_dir / "jars"

    if beans_dir.exists():
        shutil.rmtree(beans_dir)
    beans_dir.mkdir(parents=True, exist_ok=True)

    sources = list(src_dir.rglob("*.java"))
    if not sources:
        print("  [COMPILE ERROR] no Java source files found")
        return False

    sources_file = work_dir / "sources.txt"
    sources_file.write_text("\n".join(str(s) for s in sources))

    result = subprocess.run(
        [
            "javac",
            "-cp", f"{jars_dir}/*",
            "-d", str(beans_dir),
            "--release", "11",
            f"@{sources_file}",
        ],
        capture_output=True,
        text=True,
        cwd=str(work_dir),
        timeout=60,
    )

    sources_file.unlink(missing_ok=True)

    if result.returncode != 0:
        print(f"  [COMPILE ERROR] {result.stderr[:1200]}")
        return False
    return True


# -----------------------------------------------------------------------------
# MATCH EXECUTION
# -----------------------------------------------------------------------------

def run_match(
    work_dir: Path,
    n_matches: int = 3,
    timeout_ms: int = 30000,
    delay_ms: int = 1,
) -> dict:
    jars = work_dir / "jars"
    beans = work_dir / "beans"
    log_dir = work_dir / "rl_logs"
    log_dir.mkdir(exist_ok=True)

    java_cmd = [
        "java",
        "-cp", f"{jars}/*:{beans}",
        "supportGUI.HeadlessMatchRunner",
        str(n_matches),
        str(timeout_ms),
        str(delay_ms),
        str(log_dir),
    ]

    if shutil.which("xvfb-run") and not os.environ.get("DISPLAY"):
        cmd = ["xvfb-run", "-a"] + java_cmd
    else:
        cmd = java_cmd

    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            cwd=str(work_dir),
            timeout=(timeout_ms / 1000.0 * n_matches + 60),
        )
        output = result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        return {
            "scoreA": 0.0,
            "scoreB": 0.0,
            "winRateA": 0.0,
            "avgDeadMainA": 0.0,
            "avgDeadSecA": 0.0,
            "avgDeadMainB": 0.0,
            "avgDeadSecB": 0.0,
            "avgHpA": 0.0,
            "avgHpB": 0.0,
            "error": "timeout",
        }

    return parse_match_output(output)


def parse_match_output(output: str) -> dict:
    result = {
        "scoreA": 0.0,
        "scoreB": 0.0,
        "winRateA": 0.5,
        "avgDeadMainA": 0.0,
        "avgDeadSecA": 0.0,
        "avgDeadMainB": 0.0,
        "avgDeadSecB": 0.0,
        "avgHpA": 0.0,
        "avgHpB": 0.0,
        "strategyMainA": None,
        "strategySecA": None,
        "strategyMainB": None,
        "strategySecB": None,
        "error": None,
    }

    m = re.search(r"avgScoreA=([\d.]+)\s+avgScoreB=([\d.]+)", output)
    if m:
        result["scoreA"] = float(m.group(1))
        result["scoreB"] = float(m.group(2))

    m = re.search(r"winRateA=([\d.]+)", output)
    if m:
        result["winRateA"] = float(m.group(1))

    m = re.search(r"avgDeadMainA=([\d.]+)\s+avgDeadSecA=([\d.]+)", output)
    if m:
        result["avgDeadMainA"] = float(m.group(1))
        result["avgDeadSecA"] = float(m.group(2))

    m = re.search(r"avgDeadMainB=([\d.]+)\s+avgDeadSecB=([\d.]+)", output)
    if m:
        result["avgDeadMainB"] = float(m.group(1))
        result["avgDeadSecB"] = float(m.group(2))

    m = re.search(r"avgHpA=([\d.]+)\s+avgHpB=([\d.]+)", output)
    if m:
        result["avgHpA"] = float(m.group(1))
        result["avgHpB"] = float(m.group(2))

    m = re.search(r"TeamA strategies: main=([\w.]+) secondary=([\w.]+)", output)
    if m:
        result["strategyMainA"] = m.group(1)
        result["strategySecA"] = m.group(2)

    m = re.search(r"TeamB strategies: main=([\w.]+) secondary=([\w.]+)", output)
    if m:
        result["strategyMainB"] = m.group(1)
        result["strategySecB"] = m.group(2)

    if "SUMMARY" not in output:
        result["error"] = "no_summary"

    return result


# -----------------------------------------------------------------------------
# FITNESS
# -----------------------------------------------------------------------------

def compute_fitness(result: dict) -> float:
    """
    Team A fitness against fixed MacDuo.

    scoreA        : main composite signal from runner
    ally_survival : penalizes allied deaths
    kill_pressure : rewards enemy deaths
    hp_dominance  : rewards ending with more HP than enemy
    """

    score = result["scoreA"]
    dead_ally = result["avgDeadMainA"] + result["avgDeadSecA"]
    dead_enemy = result["avgDeadMainB"] + result["avgDeadSecB"]
    hp_self = result["avgHpA"]
    hp_enemy = result["avgHpB"]

    ally_survival = max(0.0, 1.0 - dead_ally / TEAM_SIZE)
    kill_pressure = max(0.0, dead_enemy / TEAM_SIZE)
    hp_dominance = max(0.0, hp_self - hp_enemy)

    fitness = (
        score * (0.6 + 0.4 * ally_survival)
        + 0.15 * kill_pressure
        + 0.10 * hp_dominance
    )
    return float(fitness)


# -----------------------------------------------------------------------------
# WORKER SETUP / EVALUATION
# -----------------------------------------------------------------------------

def setup_worker(worker_id: int | str) -> Path:
    worker_dir = RL_DIR / "rl_workers" / f"worker_{worker_id}"
    worker_dir.mkdir(parents=True, exist_ok=True)

    for name in ["jars", "avatars", "META-INF"]:
        link = worker_dir / name
        target = RL_DIR / name
        if target.exists():
            if link.is_symlink() or link.exists():
                if link.is_dir() and not link.is_symlink():
                    shutil.rmtree(link)
                else:
                    link.unlink()
            link.symlink_to(target)

    src_dst = worker_dir / "src"
    if src_dst.exists():
        shutil.rmtree(src_dst)
    shutil.copytree(RL_DIR / "src", src_dst)

    return worker_dir


def evaluate_candidate(args):
    """
    Evaluate one candidate in normalized [0,1] space.
    Returns (candidate_index, fitness, result_dict).
    """
    candidate_idx, worker_id, norm_a, n_matches, timeout_ms = args

    # Generate unique worker ID to avoid race conditions
    unique_worker_id = f"{worker_id}_{uuid.uuid4().hex[:8]}"
    worker_dir = setup_worker(unique_worker_id)
    
    try:
        real_a = denormalize(norm_a)

        build_team_sources(worker_dir, team_pkg="rla", suffix="A", config_class="RLConfigA")
        generate_rlconfig_java(
            real_a,
            worker_dir / "src" / "algorithms" / "rla" / "RLConfigA.java",
            "RLConfigA",
            "algorithms.rla",
        )

        patch_parameters_for_fixed_duel(worker_dir)

        if not compile_java(worker_dir):
            return candidate_idx, -1.0, {"error": "compile_failed"}

        result = run_match(worker_dir, n_matches=n_matches, timeout_ms=timeout_ms)

        if result.get("strategyMainA") and result.get("strategyMainB"):
            print(
                f"  [Worker {worker_id}] "
                f"A: {result['strategyMainA'].split('.')[-1]} "
                f"vs B: {result['strategyMainB'].split('.')[-1]}"
            )

        if result.get("error"):
            return candidate_idx, -0.5, result

        fitness = compute_fitness(result)
        return candidate_idx, fitness, result

    finally:
        if worker_dir.exists():
            shutil.rmtree(worker_dir, ignore_errors=True)


# -----------------------------------------------------------------------------
# CMA-ES
# -----------------------------------------------------------------------------

class CMAES:
    def __init__(self, x0: np.ndarray, sigma0: float = 0.2, pop_size: int | None = None):
        self.n = len(x0)
        self.mean = x0.copy()
        self.sigma = float(sigma0)

        self.lam = pop_size or (4 + int(3 * math.log(self.n)))
        self.mu = self.lam // 2

        weights = np.log(self.mu + 0.5) - np.log(np.arange(1, self.mu + 1))
        self.weights = weights / weights.sum()
        self.mueff = 1.0 / np.sum(self.weights ** 2)

        self.cs = (self.mueff + 2) / (self.n + self.mueff + 5)
        self.ds = 1 + 2 * max(0.0, math.sqrt((self.mueff - 1) / (self.n + 1)) - 1) + self.cs
        self.chiN = math.sqrt(self.n) * (1 - 1 / (4 * self.n) + 1 / (21 * self.n**2))
        self.cc = (4 + self.mueff / self.n) / (self.n + 4 + 2 * self.mueff / self.n)
        self.c1 = 2 / ((self.n + 1.3) ** 2 + self.mueff)
        self.cmu = min(
            1 - self.c1,
            2 * (self.mueff - 2 + 1 / self.mueff) / ((self.n + 2) ** 2 + self.mueff),
        )

        self.ps = np.zeros(self.n)
        self.pc = np.zeros(self.n)

        self.C = np.eye(self.n)
        self.sqrtC = np.eye(self.n)
        self.invsqrtC = np.eye(self.n)

        self.eigeneval = 0
        self.gen = 0

    def ask(self) -> list[np.ndarray]:
        solutions = []
        for _ in range(self.lam):
            z = np.random.randn(self.n)
            x = self.mean + self.sigma * (self.sqrtC @ z)
            x = np.clip(x, 0.0, 1.0)
            solutions.append(x)
        return solutions

    def tell(self, solutions: list[np.ndarray], fitnesses: list[float]):
        self.gen += 1

        idx = np.argsort(fitnesses)[::-1]
        old_mean = self.mean.copy()

        self.mean = np.zeros(self.n)
        for i in range(self.mu):
            self.mean += self.weights[i] * solutions[idx[i]]

        y = (self.mean - old_mean) / max(self.sigma, 1e-12)
        z = self.invsqrtC @ y

        self.ps = (
            (1 - self.cs) * self.ps
            + math.sqrt(self.cs * (2 - self.cs) * self.mueff) * z
        )

        norm_ps = np.linalg.norm(self.ps)
        denom = math.sqrt(1 - (1 - self.cs) ** (2 * self.gen))
        hsig = (norm_ps / max(denom, 1e-12) / self.chiN) < (1.4 + 2 / (self.n + 1))

        self.pc = (
            (1 - self.cc) * self.pc
            + (1.0 if hsig else 0.0) * math.sqrt(self.cc * (2 - self.cc) * self.mueff) * y
        )

        artmp = np.zeros((self.n, self.mu))
        for i in range(self.mu):
            artmp[:, i] = (solutions[idx[i]] - old_mean) / max(self.sigma, 1e-12)

        self.C = (
            (1 - self.c1 - self.cmu) * self.C
            + self.c1 * (
                np.outer(self.pc, self.pc)
                + (0.0 if hsig else 1.0) * self.cc * (2 - self.cc) * self.C
            )
            + self.cmu * artmp @ np.diag(self.weights) @ artmp.T
        )

        self.sigma *= math.exp((self.cs / self.ds) * (np.linalg.norm(self.ps) / self.chiN - 1))

        if self.gen - self.eigeneval > self.lam / (self.c1 + self.cmu) / self.n / 10:
            self.eigeneval = self.gen
            self.C = np.triu(self.C) + np.triu(self.C, 1).T

            eigvals, eigvecs = np.linalg.eigh(self.C)
            eigvals = np.maximum(eigvals, 1e-20)

            sqrtD = np.sqrt(eigvals)
            self.sqrtC = eigvecs @ np.diag(sqrtD) @ eigvecs.T
            self.invsqrtC = eigvecs @ np.diag(1.0 / sqrtD) @ eigvecs.T

    @property
    def best_params(self) -> np.ndarray:
        return denormalize(self.mean)


# -----------------------------------------------------------------------------
# CHECKPOINTS / EXPORTS
# -----------------------------------------------------------------------------

def save_checkpoint(
    run_dir: Path,
    gen: int,
    params_a: np.ndarray,
    fitness_a: float,
    history: list[dict],
):
    checkpoint = {
        "generation": gen,
        "best_fitness_A": float(fitness_a),
        "paramsA": {PARAM_NAMES[i]: float(params_a[i]) for i in range(N_PARAMS)},
        "history": history,
    }

    (run_dir / f"checkpoint_gen{gen:04d}.json").write_text(json.dumps(checkpoint, indent=2))
    (run_dir / "latest.json").write_text(json.dumps(checkpoint, indent=2))

    generate_rlconfig_java(
        params_a,
        run_dir / f"RLConfigA_gen{gen:04d}.java",
        f"RLConfigA_gen{gen:04d}",
        "algorithms.rl",
    )


def export_best_policy(run_dir: Path, params_a: np.ndarray, fitness_a: float):
    best_dir = run_dir / "best_policy"
    best_dir.mkdir(exist_ok=True)

    payload_a = {
        "team": "A",
        "best_fitness": float(fitness_a),
        "params": {PARAM_NAMES[i]: float(params_a[i]) for i in range(N_PARAMS)},
    }

    (best_dir / "teamA_best_policy.json").write_text(json.dumps(payload_a, indent=2))
    generate_rlconfig_java(
        params_a,
        best_dir / "RLConfig_best_A.java",
        "RLConfig_best_A",
        "algorithms.rl",
    )


# -----------------------------------------------------------------------------
# TRAINING LOOP
# -----------------------------------------------------------------------------

def train(args):
    print("=" * 70)
    print("  CMA-ES trainer for Simovie Battlebot")
    print("  Fixed-opponent mode: Team A vs MacDuo")
    print("=" * 70)

    results_dir = RL_DIR / "rl_results"
    results_dir.mkdir(exist_ok=True)

    run_id = time.strftime("%Y%m%d_%H%M%S")
    run_dir = results_dir / f"run_{run_id}"
    run_dir.mkdir()

    start_params_a = normalize(PARAM_DEFAULT)
    cma = CMAES(start_params_a, sigma0=0.2, pop_size=args.pop)

    best_fitness_a = -float("inf")
    best_params_a = denormalize(start_params_a)
    history = []

    for gen in range(args.generations):
        t0 = time.time()
        print(f"\n--- Generation {gen + 1}/{args.generations} ---")

        candidates = cma.ask()
        fitnesses = [-1.0] * len(candidates)
        results = [None] * len(candidates)

        eval_args = [
            (idx, idx % args.workers, candidates[idx], args.matches, args.timeout)
            for idx in range(len(candidates))
        ]

        with ProcessPoolExecutor(max_workers=args.workers) as executor:
            futures = {executor.submit(evaluate_candidate, arg): arg[0] for arg in eval_args}

            for future in as_completed(futures):
                idx = futures[future]
                try:
                    candidate_idx, fit, result = future.result()
                    fitnesses[candidate_idx] = fit
                    results[candidate_idx] = result
                except Exception as e:
                    print(f"  [ERROR] Candidate {idx}: {e}")
                    fitnesses[idx] = -1.0
                    results[idx] = {"error": str(e)}

        cma.tell(candidates, fitnesses)

        gen_best_idx = int(np.argmax(fitnesses))
        gen_best_fit = float(fitnesses[gen_best_idx])
        gen_best_params = denormalize(candidates[gen_best_idx])

        if gen_best_fit > best_fitness_a:
            best_fitness_a = gen_best_fit
            best_params_a = gen_best_params.copy()

        if cma.sigma < SIGMA_MIN:
            print(f"  [CMA] Sigma collapse ({cma.sigma:.4f}) -> restart around current best")
            cma = CMAES(normalize(best_params_a), sigma0=SIGMA_RESTART, pop_size=args.pop)

        elapsed = time.time() - t0

        best_result = results[gen_best_idx] or {}
        scoreA = best_result.get("scoreA", 0.0)
        winRateA = best_result.get("winRateA", 0.0)

        print(f"  Gen best fitness : {gen_best_fit:.4f}")
        print(f"  Gen best scoreA  : {scoreA:.4f}")
        print(f"  Gen best winRate : {winRateA:.4f}")
        print(f"  Global best      : {best_fitness_a:.4f}")
        print(f"  Sigma            : {cma.sigma:.4f}")
        print(f"  Time             : {elapsed:.1f}s")

        history.append({
            "gen": gen + 1,
            "best_a": float(best_fitness_a),
            "gen_best_a": float(gen_best_fit),
            "sigma_a": float(cma.sigma),
            "scoreA": float(scoreA),
            "winRateA": float(winRateA),
        })

        if (gen + 1) % 5 == 0 or gen == args.generations - 1:
            save_checkpoint(run_dir, gen + 1, best_params_a, best_fitness_a, history)

    print("\nTRAINING COMPLETE")

    final_a = RL_DIR / "src" / "algorithms" / "rl" / "RLConfig_best_A.java"
    generate_rlconfig_java(best_params_a, final_a, "RLConfig_best_A", "algorithms.rl")

    export_best_policy(run_dir, best_params_a, best_fitness_a)

    print("\nBest fitness:", f"{best_fitness_a:.4f}")
    print("Best config exported to:")
    print(f"  - {final_a}")
    print(f"  - {run_dir / 'best_policy'}")


# -----------------------------------------------------------------------------
# MAIN
# -----------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--generations", type=int, default=50)
    parser.add_argument("--pop",         type=int, default=14)
    parser.add_argument("--matches",     type=int, default=3)
    parser.add_argument("--workers",     type=int, default=4)
    parser.add_argument("--timeout",     type=int, default=30000)
    parser.add_argument("--resume-a",    type=str, default=None)
    parser.add_argument("--resume-b",    type=str, default=None)
    args = parser.parse_args()

    train(args)
