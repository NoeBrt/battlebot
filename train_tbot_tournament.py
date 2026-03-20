#!/usr/bin/env python3
"""
train_tbot_tournament.py

CMA-ES tournament trainer for TacticalBot (TBot).
Optimizes 72 parameters across a gauntlet of opponents.

Usage:
  python3 train_tbot_tournament.py --generations 30 --pop 48 --sigma0 0.3 --workers 8
  python3 train_tbot_tournament.py -c rl_results/.../checkpoint.json --generations 20
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
# PARAMETER SPACE (72 tunable parameters)
# -----------------------------------------------------------------------------

PARAM_SPEC = [
    # Target Selection (10)
    ("STALE_TTL",             70,    30,   200,  True),
    ("TGT_PROXIMITY_WEIGHT", 800.0, 200, 1500,  False),
    ("TGT_TYPE_BONUS_SEC",   200.0,   0,  500,  False),
    ("TGT_LOWHP_CRIT",      350.0,   0,  600,  False),
    ("TGT_LOWHP_MOD",       180.0,   0,  400,  False),
    ("TGT_FOCUS_BONUS",     400.0,   0,  800,  False),
    ("TGT_SAFEFIRE",         120.0,   0,  300,  False),
    ("TGT_STALE_PENALTY",     5.0,   0,   20,  False),
    ("TGT_DMG_COMMITTED",   200.0,   0,  500,  False),
    ("TGT_LAST_HIT_BONUS",  150.0,   0,  400,  False),

    # Main Utility Weights (16)
    ("U_ADVANCE_BASE",        0.4, 0.0, 1.0, False),
    ("U_ADVANCE_NO_ENEMY",    0.6, 0.0, 1.0, False),
    ("U_COMBAT_HAS_TARGET",   0.8, 0.0, 1.5, False),
    ("U_COMBAT_SAFEFIRE",     0.3, 0.0, 1.0, False),
    ("U_FLANK_BLOCKED_TICKS", 0.5, 0.0, 1.0, False),
    ("U_FLANK_BLOCKED_THRESH", 20,   5,  50, True),
    ("U_RETREAT_HP_FACTOR",   0.7, 0.0, 1.0, False),
    ("U_RETREAT_HP_THRESH",  80.0,  20, 200, False),
    ("U_RETREAT_ALONE",       0.4, 0.0, 1.0, False),
    ("U_HOLD_AT_POS",         0.3, 0.0, 1.0, False),
    ("U_REGROUP_SPREAD",      0.3, 0.0, 1.0, False),
    ("U_REGROUP_DIST_THRESH",500.0,200, 800, False),
    ("U_ANCHOR_BONUS",        0.2, 0.0, 0.8, False),
    ("U_FLANKER_BONUS",       0.2, 0.0, 0.8, False),
    ("U_AGGRO_ALIVE_THRESH",    3,   1,   5, True),
    ("U_AGGRO_HP_THRESH",   200.0, 100, 300, False),

    # Secondary Utility Weights (8)
    ("US_SCOUT_BASE",         0.5, 0.0, 1.0, False),
    ("US_ASSASSIN_BASE",      0.6, 0.0, 1.0, False),
    ("US_ASSASSIN_HP_THRESH", 60.0,  20, 100, False),
    ("US_ESCORT_BASE",        0.4, 0.0, 1.0, False),
    ("US_RETREAT_HP_PCT",     0.35,0.1, 0.6, False),
    ("US_EVADE_RANGE",       250.0, 100, 500, False),
    ("US_DEEP_SCOUT_X",    1600.0,1200,2200, False),
    ("US_FLANK_Y",           300.0, 150, 600, False),

    # Potential Field (14)
    ("PF_ENEMY_REPEL",        2.5, 0.5, 5.0, False),
    ("PF_ENEMY_ATTRACT",      0.6, 0.1, 2.0, False),
    ("PF_TANGENTIAL",         0.7, 0.1, 2.0, False),
    ("PF_ALLY_REPEL_RANGE", 200.0,  50, 400, False),
    ("PF_ALLY_REPEL_STR",     0.9, 0.1, 3.0, False),
    ("PF_WALL_STR",           0.6, 0.1, 2.0, False),
    ("PF_WRECK_RANGE",      150.0,  50, 300, False),
    ("PF_WRECK_STR",          0.5, 0.1, 2.0, False),
    ("PF_FOCUS_PULL",         0.3, 0.0, 1.5, False),
    ("PF_FORMATION_PULL",     0.2, 0.0, 1.0, False),
    ("PF_FLANKER_PERP",       0.4, 0.0, 1.5, False),
    ("PF_ANCHOR_CENTER",      0.3, 0.0, 1.0, False),
    ("PF_SEC_EVADE",          1.5, 0.5, 3.0, False),
    ("PF_SEC_TANGENTIAL",     1.0, 0.1, 2.0, False),

    # Kiting Ranges (6)
    ("KITE_MIN_AGGRO",      300.0, 150, 500, False),
    ("KITE_MAX_AGGRO",      700.0, 400, 900, False),
    ("KITE_MIN_NORMAL",     360.0, 200, 600, False),
    ("KITE_MAX_NORMAL",     780.0, 500,1000, False),
    ("KITE_MIN_DEFEN",      420.0, 300, 700, False),
    ("KITE_MAX_DEFEN",      900.0, 600,1200, False),

    # Formation (10)
    ("FORM_HOLD_X_OFFSET", 1100.0, 600,1600, False),
    ("FORM_Y_SPREAD",       260.0, 100, 500, False),
    ("FORM_ANCHOR_X_ADJ",     0.0,-200, 200, False),
    ("FORM_FLANKER_Y_ADJ",  100.0,   0, 300, False),
    ("FORM_RETREAT_X",      300.0, 150, 500, False),
    ("FORM_REGROUP_X",      600.0, 300,1000, False),
    ("FORM_SEC_ESCORT_OFFSET",150.0,50, 400, False),
    ("FORM_SEC_FLANK_Y_OFFSET",400.0,200,600,False),
    ("FORM_ADAPT_DEAD_MAIN",  0.3, 0.0, 0.5, False),
    ("FORM_ADAPT_DEAD_SEC",   0.15,0.0, 0.3, False),

    # Firing (4)
    ("FIRE_NOFIRE_THRESH",     20,   5,  50, True),
    ("FIRE_ANGLE_OFFSETS",      7,   3,   9, True),
    ("FIRE_SEC_ENGAGE_RANGE",600.0,300, 900, False),
    ("FIRE_LEAD_ACCEL_FACTOR", 0.25,0.0,0.5, False),

    # Coordination (4)
    ("COORD_FOCUS_RADIUS",  120.0,  50, 300, False),
    ("COORD_ROLE_SWAP_CD",    100,  30, 300, True),
    ("COORD_THREAT_RADIUS", 400.0, 200, 800, False),
    ("COORD_REGROUP_HP_THRESH",150.0,50,250, False),
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
    "BOT_R": 50.0,
    "WALL_MARGIN": 200.0,
}

RL_DIR = Path(__file__).resolve().parent
SIGMA_MIN = 0.05
SIGMA_RESTART = 0.40
TEAM_SIZE = 5.0
TEAM_TOTAL_HP = 1100.0

TOURNAMENT_OPPONENTS = [
    {"name": "MacDuo",     "main": "algorithms.external.MacDuoMain",       "sec": "algorithms.external.MacDuoSecondary",       "weight": 5.0},
    {"name": "Himself",    "main": "algorithms.tbot_b.TBotMainB",          "sec": "algorithms.tbot_b.TBotSecondaryB",           "weight": 3.0},
    {"name": "MacDuoUltra","main": "algorithms.external.MacDuoUltraMain",  "sec": "algorithms.external.MacDuoUltraSecondary",  "weight": 2.0},
    {"name": "Stage8",     "main": "algorithms.external.Stage8MainA",      "sec": "algorithms.external.Stage8SecondaryA",       "weight": 2.0},
    {"name": "MecaMouse",  "main": "algorithms.external.MecaMouseMain",    "sec": "algorithms.external.MecaMouseSecondary",     "weight": 1.5},
    {"name": "Superhero",  "main": "algorithms.external.SuperheroAMain",   "sec": "algorithms.external.SuperheroASecondary",    "weight": 1.5},
    {"name": "FifthElem",  "main": "algorithms.external.FifthElementMain", "sec": "algorithms.external.FifthElementSecondary",  "weight": 1.0},
    {"name": "PrevRLBot",  "main": "algorithms.rl.RLBotMain",              "sec": "algorithms.rl.RLBotSecondary",               "weight": 1.0},
    {"name": "Claude",     "main": "algorithms.LLMS.ClaudeMain",           "sec": "algorithms.LLMS.ClaudeSecondary",            "weight": 1.0},
    {"name": "AdaptiveK",  "main": "algorithms.LLMS.AdaptiveKiteMain",     "sec": "algorithms.LLMS.AdaptiveKiteSecondary",      "weight": 1.0},
    {"name": "Yomi",       "main": "algorithms.YomiMain",                  "sec": "algorithms.YomiSecondary",                    "weight": 4.0},
]

# -----------------------------------------------------------------------------
# NORMALIZATION / REPAIR
# -----------------------------------------------------------------------------

def normalize(real_params: np.ndarray) -> np.ndarray:
    return (real_params - PARAM_LO) / (PARAM_HI - PARAM_LO)

def denormalize(norm_params: np.ndarray) -> np.ndarray:
    n = reflect_to_unit(norm_params)
    return PARAM_LO + n * (PARAM_HI - PARAM_LO)

def reflect_to_unit(x: np.ndarray) -> np.ndarray:
    y = np.array(x, dtype=float, copy=True)
    for i in range(len(y)):
        while y[i] < 0.0 or y[i] > 1.0:
            if y[i] < 0.0: y[i] = -y[i]
            if y[i] > 1.0: y[i] = 2.0 - y[i]
    return np.clip(y, 0.0, 1.0)

# -----------------------------------------------------------------------------
# JAVA CODE GENERATION
# -----------------------------------------------------------------------------

def generate_tbotconfig_java(params: np.ndarray, filepath: Path, class_name: str = "TBotConfig", package_name: str = "algorithms.tbot"):
    lines = [f"package {package_name};", "", f"public class {class_name} {{"]
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

    base_src = (src_root / "tbot" / "TBotBase.java").read_text()
    main_src = (src_root / "tbot" / "TBotMain.java").read_text()
    sec_src  = (src_root / "tbot" / "TBotSecondary.java").read_text()

    def transform(src: str, old_cls: str, new_cls: str) -> str:
        src = src.replace("package algorithms.tbot;", f"package algorithms.{team_pkg};")
        src = src.replace("TBotConfig", config_class)
        src = src.replace(old_cls, new_cls)
        return src

    base_out = transform(base_src, "TBotBase", f"TBotBase{suffix}")
    main_out = transform(main_src, "TBotMain", f"TBotMain{suffix}")
    sec_out  = transform(sec_src,  "TBotSecondary", f"TBotSecondary{suffix}")

    main_out = main_out.replace(f"extends TBotBase{suffix} {{", f"extends TBotBase{suffix} {{")
    main_out = main_out.replace("extends TBotBase {", f"extends TBotBase{suffix} {{")
    sec_out  = sec_out.replace("extends TBotBase {", f"extends TBotBase{suffix} {{")

    (team_dir / f"TBotBase{suffix}.java").write_text(base_out)
    (team_dir / f"TBotMain{suffix}.java").write_text(main_out)
    (team_dir / f"TBotSecondary{suffix}.java").write_text(sec_out)


def patch_parameters_for_duel(worker_dir: Path, opp_main: str, opp_sec: str):
    p = worker_dir / "src" / "characteristics" / "Parameters.java"
    if not p.exists():
        return
    txt = p.read_text()
    txt = re.sub(
        r'teamAMainBotBrainClassName\s*=\s*".*?";',
        f'teamAMainBotBrainClassName = "algorithms.tbot_a.TBotMainA";',
        txt)
    txt = re.sub(
        r'teamASecondaryBotBrainClassName\s*=\s*".*?";',
        f'teamASecondaryBotBrainClassName = "algorithms.tbot_a.TBotSecondaryA";',
        txt)
    txt = re.sub(
        r'teamBMainBotBrainClassName\s*=\s*".*?";',
        f'teamBMainBotBrainClassName = "{opp_main}";',
        txt)
    txt = re.sub(
        r'teamBSecondaryBotBrainClassName\s*=\s*".*?";',
        f'teamBSecondaryBotBrainClassName = "{opp_sec}";',
        txt)
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
        ["javac", "-cp", f"{jars_dir}/*", "-d", str(beans_dir),
         "--release", "17", f"@{sources_file}"],
        capture_output=True, text=True, cwd=str(work_dir), timeout=180)

    sources_file.unlink(missing_ok=True)

    if result.returncode != 0:
        print(f"  [COMPILE ERROR] {result.stderr[:1200]}")
        return False
    return True


# -----------------------------------------------------------------------------
# MATCH EXECUTION
# -----------------------------------------------------------------------------

def run_match(work_dir: Path, n_matches: int = 5, timeout_ms: int = 30000, delay_ms: int = 0) -> dict:
    jars = work_dir / "jars"
    beans = work_dir / "beans"
    log_dir = work_dir / "tbot_logs"
    log_dir.mkdir(exist_ok=True)

    java_cmd = [
        "java", "-cp", f"{jars}/*:{beans}",
        "supportGUI.HeadlessMatchRunner",
        str(n_matches), str(timeout_ms), str(delay_ms), str(log_dir)]

    cmd = ["xvfb-run", "-a"] + java_cmd if shutil.which("xvfb-run") and not os.environ.get("DISPLAY") else java_cmd

    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True,
            cwd=str(work_dir), timeout=(timeout_ms / 1000.0 * n_matches + 60))
        output = result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        return {"scoreA": 0.0, "scoreB": 0.0, "winRateA": 0.0,
                "avgDeadMainA": 0.0, "avgDeadSecA": 0.0,
                "avgDeadMainB": 0.0, "avgDeadSecB": 0.0,
                "avgHpA": 0.0, "avgHpB": 0.0, "error": "timeout"}

    return parse_match_output(output)


def parse_match_output(output: str) -> dict:
    result = {"scoreA": 0.0, "scoreB": 0.0, "winRateA": 0.0,
              "avgDeadMainA": 0.0, "avgDeadSecA": 0.0,
              "avgDeadMainB": 0.0, "avgDeadSecB": 0.0,
              "avgHpA": 0.0, "avgHpB": 0.0, "avgTimeMs": 0.0, "error": None}

    m = re.search(r"avgScoreA=([\d.]+)\s+avgScoreB=([\d.]+)", output)
    if m: result["scoreA"] = float(m.group(1)); result["scoreB"] = float(m.group(2))
    m = re.search(r"winRateA=([\d.]+)", output)
    if m: result["winRateA"] = float(m.group(1))
    m = re.search(r"avgDeadMainA=([\d.]+)\s+avgDeadSecA=([\d.]+)", output)
    if m: result["avgDeadMainA"] = float(m.group(1)); result["avgDeadSecA"] = float(m.group(2))
    m = re.search(r"avgDeadMainB=([\d.]+)\s+avgDeadSecB=([\d.]+)", output)
    if m: result["avgDeadMainB"] = float(m.group(1)); result["avgDeadSecB"] = float(m.group(2))
    m = re.search(r"avgHpA=([\d.]+)\s+avgHpB=([\d.]+)", output)
    if m: result["avgHpA"] = float(m.group(1)); result["avgHpB"] = float(m.group(2))
    m = re.search(r"avgTimeMs=([\d.]+)", output)
    if m: result["avgTimeMs"] = float(m.group(1))
    if "SUMMARY" not in output: result["error"] = "no_summary"
    return result


# -----------------------------------------------------------------------------
# FITNESS
# -----------------------------------------------------------------------------

def compute_fitness(result: dict, timeout_ms: int = 60000) -> float:
    score_a = float(result.get("scoreA", 0.0))
    score_b = float(result.get("scoreB", 0.0))
    win_a = float(result.get("winRateA", 0.0))
    dead_a = float(result.get("avgDeadMainA", 0.0) + result.get("avgDeadSecA", 0.0))
    dead_b = float(result.get("avgDeadMainB", 0.0) + result.get("avgDeadSecB", 0.0))
    hp_a = float(result.get("avgHpA", 0.0))
    hp_b = float(result.get("avgHpB", 0.0))
    avg_time = float(result.get("avgTimeMs", timeout_ms))

    score_diff = score_a - score_b
    kill_diff = (dead_b - dead_a) / TEAM_SIZE
    hp_diff = (hp_a - hp_b) / TEAM_TOTAL_HP
    survival = 1.0 - dead_a / TEAM_SIZE

    # Speed bonus: faster wins are better. Ranges from 0 (timeout) to 1.0 (instant).
    # Only rewards speed when winning (no bonus for fast losses).
    speed_bonus = 0.0
    if win_a > 0 and avg_time > 0:
        speed_bonus = win_a * max(0.0, 1.0 - avg_time / timeout_ms)

    return float(3.0 * win_a + 1.25 * score_diff + 0.90 * kill_diff + 0.60 * hp_diff + 0.25 * survival + 0.50 * speed_bonus)


# -----------------------------------------------------------------------------
# WORKER SETUP / EVALUATION
# -----------------------------------------------------------------------------

def setup_worker(worker_id) -> Path:
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
    candidate_idx, worker_id, norm_a, norm_prev, n_matches, timeout_ms = args
    unique_worker_id = f"{worker_id}_{uuid.uuid4().hex[:8]}"
    worker_dir = setup_worker(unique_worker_id)

    try:
        real_a = denormalize(norm_a)
        real_prev = denormalize(norm_prev)

        # Team A: the candidate
        build_team_sources(worker_dir, team_pkg="tbot_a", suffix="A", config_class="TBotConfigA")
        generate_tbotconfig_java(real_a, worker_dir / "src" / "algorithms" / "tbot_a" / "TBotConfigA.java", "TBotConfigA", "algorithms.tbot_a")

        # Team B (for self-play): previous best
        build_team_sources(worker_dir, team_pkg="tbot_b", suffix="B", config_class="TBotConfigB")
        generate_tbotconfig_java(real_prev, worker_dir / "src" / "algorithms" / "tbot_b" / "TBotConfigB.java", "TBotConfigB", "algorithms.tbot_b")

        if not compile_java(worker_dir):
            return candidate_idx, -10.0, {"error": "compile_failed"}

        total_fitness = 0.0
        total_weight = 0.0
        results_agg = {"winRateA": 0.0, "scoreA": 0.0, "scoreB": 0.0,
                       "avgHpA": 0.0, "avgHpB": 0.0, "per_opp": {}}

        for opp in TOURNAMENT_OPPONENTS:
            patch_parameters_for_duel(worker_dir, opp["main"], opp["sec"])
            if not compile_java(worker_dir):
                continue

            res = run_match(worker_dir, n_matches=n_matches, timeout_ms=timeout_ms)
            if res.get("error"):
                continue

            match_fit = compute_fitness(res, timeout_ms)
            w = opp["weight"]
            total_fitness += match_fit * w
            total_weight += w

            results_agg["per_opp"][opp["name"]] = {"fit": match_fit, "win": res.get("winRateA", 0.0)}
            results_agg["winRateA"] += res.get("winRateA", 0.0) * w
            results_agg["scoreA"]   += res.get("scoreA", 0.0) * w
            results_agg["scoreB"]   += res.get("scoreB", 0.0) * w
            results_agg["avgHpA"]   += res.get("avgHpA", 0.0) * w
            results_agg["avgHpB"]   += res.get("avgHpB", 0.0) * w

        if total_weight > 0:
            final_fit = total_fitness / total_weight
            for k in ["winRateA", "scoreA", "scoreB", "avgHpA", "avgHpB"]:
                results_agg[k] /= total_weight
        else:
            final_fit = -5.0

        return candidate_idx, final_fit, results_agg
    except Exception as e:
        print(f"Exception in worker: {e}")
        return candidate_idx, -10.0, {"error": "exception"}
    finally:
        if worker_dir.exists():
            shutil.rmtree(worker_dir, ignore_errors=True)


def reevaluate_params(norm_a, norm_prev, worker_id, n_matches, timeout_ms):
    _, fitness, result = evaluate_candidate((-1, worker_id, norm_a, norm_prev, n_matches, timeout_ms))
    return fitness, result


# -----------------------------------------------------------------------------
# CMA-ES
# -----------------------------------------------------------------------------

class CMAES:
    def __init__(self, x0, sigma0=0.2, pop_size=None):
        self.n = len(x0)
        self.mean = reflect_to_unit(x0)
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
        self.cmu = min(1 - self.c1, 2 * (self.mueff - 2 + 1 / self.mueff) / ((self.n + 2) ** 2 + self.mueff))

        self.ps = np.zeros(self.n)
        self.pc = np.zeros(self.n)
        self.C = np.eye(self.n)
        self.sqrtC = np.eye(self.n)
        self.invsqrtC = np.eye(self.n)
        self.eigeneval = 0
        self.gen = 0

    def ask(self):
        solutions = []
        for _ in range(self.lam):
            z = np.random.randn(self.n)
            x = self.mean + self.sigma * (self.sqrtC @ z)
            x = reflect_to_unit(x)
            solutions.append(x)
        return solutions

    def tell(self, solutions, fitnesses):
        self.gen += 1
        idx = np.argsort(fitnesses)[::-1]
        old_mean = self.mean.copy()

        self.mean = np.zeros(self.n)
        for i in range(self.mu):
            self.mean += self.weights[i] * solutions[idx[i]]
        self.mean = reflect_to_unit(self.mean)

        y = (self.mean - old_mean) / max(self.sigma, 1e-12)
        z = self.invsqrtC @ y

        self.ps = (1 - self.cs) * self.ps + math.sqrt(self.cs * (2 - self.cs) * self.mueff) * z
        norm_ps = np.linalg.norm(self.ps)
        denom = math.sqrt(1 - (1 - self.cs) ** (2 * self.gen))
        hsig = (norm_ps / max(denom, 1e-12) / self.chiN) < (1.4 + 2 / (self.n + 1))

        self.pc = (1 - self.cc) * self.pc + (1.0 if hsig else 0.0) * math.sqrt(self.cc * (2 - self.cc) * self.mueff) * y

        artmp = np.zeros((self.n, self.mu))
        for i in range(self.mu):
            artmp[:, i] = (solutions[idx[i]] - old_mean) / max(self.sigma, 1e-12)

        self.C = (
            (1 - self.c1 - self.cmu) * self.C
            + self.c1 * (np.outer(self.pc, self.pc) + (0.0 if hsig else 1.0) * self.cc * (2 - self.cc) * self.C)
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


# -----------------------------------------------------------------------------
# CHECKPOINTS / EXPORTS
# -----------------------------------------------------------------------------

def save_checkpoint(run_dir, gen, params_a, fitness_a, history):
    checkpoint = {
        "generation": gen,
        "best_fitness_A": float(fitness_a),
        "paramsA": {PARAM_NAMES[i]: float(params_a[i]) for i in range(N_PARAMS)},
        "history": history,
    }
    (run_dir / f"checkpoint_gen{gen:04d}.json").write_text(json.dumps(checkpoint, indent=2))
    (run_dir / "latest.json").write_text(json.dumps(checkpoint, indent=2))


def export_best_policy(run_dir, params_a, fitness_a):
    best_dir = run_dir / "best_policy"
    best_dir.mkdir(exist_ok=True)
    payload = {"team": "A", "best_fitness": float(fitness_a),
               "params": {PARAM_NAMES[i]: float(params_a[i]) for i in range(N_PARAMS)}}
    (best_dir / "teamA_best_policy.json").write_text(json.dumps(payload, indent=2))
    generate_tbotconfig_java(params_a, best_dir / "TBotConfig_best.java", "TBotConfig_best", "algorithms.tbot")


def load_resume_params(path_str):
    if not path_str:
        return None
    p = Path(path_str)
    if not p.exists():
        raise FileNotFoundError(f"resume file not found: {p}")
    data = json.loads(p.read_text())
    params = data.get("paramsA") or data.get("params")
    if not params:
        raise ValueError(f"resume file does not contain paramsA/params: {p}")
    result = np.array(PARAM_DEFAULT, dtype=float)
    for i, name in enumerate(PARAM_NAMES):
        if name in params:
            result[i] = float(params[name])
    return result


# -----------------------------------------------------------------------------
# TRAINING LOOP
# -----------------------------------------------------------------------------

def train(args):
    if args.seed is not None:
        np.random.seed(args.seed)

    print("=" * 70)
    print("  CMA-ES trainer for TacticalBot (TBot)")
    print(f"  {N_PARAMS} parameters, tournament mode")
    print("=" * 70)

    results_dir = RL_DIR / "rl_results"
    results_dir.mkdir(exist_ok=True)
    run_id = time.strftime("%Y%m%d_%H%M%S")
    run_dir = results_dir / f"tbot_run_{run_id}"
    run_dir.mkdir()

    log_dir = RL_DIR / "logs"
    log_dir.mkdir(exist_ok=True)
    live_log_path = log_dir / f"tbot-training-{run_id}.log"
    live_log = open(live_log_path, "w", buffering=1)
    def log(msg):
        print(msg)
        live_log.write(msg + "\n")

    log(f"Live log: {live_log_path}")
    log(f"Hyperparameters: generations={args.generations} pop={args.pop} sigma0={args.sigma0} "
        f"sigma_min={SIGMA_MIN} sigma_restart={SIGMA_RESTART} "
        f"matches={args.matches} elite_matches={args.elite_matches} "
        f"restart_stagnation={args.restart_stagnation} workers={args.workers} "
        f"timeout={args.timeout} seed={args.seed}")
    if args.resume_a:
        log(f"Base config: {args.resume_a}")

    resumed = load_resume_params(args.resume_a)
    start_real = resumed if resumed is not None else PARAM_DEFAULT
    start_norm = normalize(start_real)

    cma = CMAES(start_norm, sigma0=args.sigma0, pop_size=args.pop)
    best_fitness_a = -float("inf")
    best_params_a = denormalize(start_norm)
    history = []
    stagnant_gens = 0

    for gen in range(args.generations):
        t0 = time.time()
        log(f"\n--- Generation {gen + 1}/{args.generations} ---")

        candidates = cma.ask()
        fitnesses = [-1.0] * len(candidates)
        results = [None] * len(candidates)
        eval_args = [(idx, idx % args.workers, candidates[idx], normalize(best_params_a),
                       args.matches, args.timeout) for idx in range(len(candidates))]

        with ProcessPoolExecutor(max_workers=args.workers) as executor:
            futures = {executor.submit(evaluate_candidate, arg): arg[0] for arg in eval_args}
            for future in as_completed(futures):
                idx = futures[future]
                try:
                    candidate_idx, fit, result = future.result()
                    fitnesses[candidate_idx] = fit
                    results[candidate_idx] = result
                except Exception as e:
                    log(f"  [ERROR] Candidate {idx}: {e}")
                    fitnesses[idx] = -10.0

        cma.tell(candidates, fitnesses)

        gen_best_idx = int(np.argmax(fitnesses))
        gen_best_norm = candidates[gen_best_idx]
        gen_best_fit, gen_best_result = reevaluate_params(
            gen_best_norm, normalize(best_params_a),
            f"recheck_gen_{gen+1}", args.elite_matches, args.timeout)
        gen_best_params = denormalize(gen_best_norm)

        improved = False
        if gen_best_fit > best_fitness_a:
            best_fitness_a = gen_best_fit
            best_params_a = gen_best_params.copy()
            improved = True
        else:
            incumbent_fit, _ = reevaluate_params(
                normalize(best_params_a), normalize(best_params_a),
                f"incumbent_{gen+1}", args.elite_matches, args.timeout)
            best_fitness_a = incumbent_fit

        stagnant_gens = 0 if improved else (stagnant_gens + 1)
        if cma.sigma < SIGMA_MIN or stagnant_gens >= args.restart_stagnation:
            why = f"sigma collapse ({cma.sigma:.4f})" if cma.sigma < SIGMA_MIN else f"stagnation ({stagnant_gens} gens)"
            log(f"  [CMA] Restart due to {why}")
            cma = CMAES(normalize(best_params_a), sigma0=SIGMA_RESTART, pop_size=args.pop)
            stagnant_gens = 0

        elapsed = time.time() - t0
        log(f"  Gen best fitness : {gen_best_fit:.4f}")
        log(f"  Gen best winRate : {gen_best_result.get('winRateA', 0.0):.4f}")
        log(f"  Global best      : {best_fitness_a:.4f}")
        log(f"  Sigma            : {cma.sigma:.4f}")
        log(f"  Time             : {elapsed:.1f}s")
        if "per_opp" in gen_best_result:
            log("  --- Opponent Breakdown ---")
            for opp_name, o_res in gen_best_result["per_opp"].items():
                log(f"      vs {opp_name:12} : Fit={o_res['fit']:>6.3f} | WinRate={o_res['win']:>4.2f}")

        history.append({
            "gen": gen + 1,
            "best_a": float(best_fitness_a),
            "gen_best_a": float(gen_best_fit),
            "sigma_a": float(cma.sigma),
            "winRateA": float(gen_best_result.get("winRateA", 0.0)),
        })
        save_checkpoint(run_dir, gen + 1, best_params_a, best_fitness_a, history)

    log("\nTRAINING COMPLETE")
    export_best_policy(run_dir, best_params_a, best_fitness_a)
    log(f"\nBest fitness: {best_fitness_a:.4f}")
    log(f"Best config: {run_dir / 'best_policy'}")
    live_log.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="CMA-ES trainer for TacticalBot")
    parser.add_argument("--generations", type=int, default=40)
    parser.add_argument("--pop", type=int, default=48)
    parser.add_argument("--matches", type=int, default=5)
    parser.add_argument("--elite-matches", type=int, default=11)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--timeout", type=int, default=30000)
    parser.add_argument("-c", "--base-config", dest="resume_a", type=str, default=None,
                        help="JSON checkpoint to seed initial params from")
    parser.add_argument("--sigma0", type=float, default=0.30)
    parser.add_argument("--sigma-min", type=float, default=None)
    parser.add_argument("--sigma-restart", type=float, default=None)
    parser.add_argument("--restart-stagnation", type=int, default=5)
    parser.add_argument("--seed", type=int, default=None)
    args = parser.parse_args()

    if args.sigma_min is not None:
        SIGMA_MIN = args.sigma_min
    if args.sigma_restart is not None:
        SIGMA_RESTART = args.sigma_restart

    train(args)
