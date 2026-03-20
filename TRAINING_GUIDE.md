# TBot CMA-ES Training Guide

## Overview

TBot (TacticalBot) is a multi-agent combat strategy for the Simovies robot simulator. Its behavior is governed by **72 numeric parameters** that control target selection, movement, formation, firing, and coordination. These parameters are optimized using **CMA-ES** (Covariance Matrix Adaptation Evolution Strategy), a derivative-free evolutionary optimizer well-suited for high-dimensional continuous search spaces.

Each candidate solution is a full 72-parameter configuration. Candidates are evaluated in a **tournament** against 11 different opponents, and their fitness is the weighted average performance across all matchups.

Training script: `train_tbot_tournament.py`

---

## 1. CMA-ES Algorithm

CMA-ES maintains a multivariate Gaussian distribution over the 72-dimensional parameter space and iteratively refines it:

### Population & Selection
- **Population size (λ)**: 48 candidates per generation (configurable via `--pop`)
- **Parent count (μ)**: λ/2 = 24 — the top 24 candidates by fitness
- **Weighted recombination**: Parents are ranked and weighted logarithmically — higher-ranked solutions contribute more to the next generation's mean

### Normalization
All 72 parameters are internally normalized to **[0, 1]** space:
```
normalized = (value - low) / (high - low)
```
When CMA-ES samples outside [0, 1], values are reflected back into the valid range.

### Key CMA-ES Components
| Component | Role |
|-----------|------|
| **Mean (m)** | Center of the search distribution — moves toward high-fitness regions |
| **Sigma (σ)** | Step size — controls exploration radius |
| **Covariance matrix (C)** | Learns parameter correlations — stretches search along promising directions |
| **Evolution path (pσ, pc)** | Momentum terms that track cumulative search direction |

### Sigma (Step Size)
- **Initial sigma**: 0.80 (high exploration, configurable via `--sigma0`)
- **Sigma adapts** each generation based on the evolution path length vs expected random walk
- If sigma is too large, CMA-ES shrinks it; if too small, it grows

### Restart Mechanism
CMA-ES restarts when progress stalls:
- **Sigma collapse**: If σ < 0.05 → restart with σ = 0.40
- **Stagnation**: If no improvement for ≥ 5 consecutive generations → restart with σ = 0.40
- On restart, the mean is seeded from the current best solution, preserving progress

### Elite Re-evaluation
After each generation:
1. The generation's best candidate is **re-evaluated** with more matches (`--elite-matches`, default 11) for statistical confidence
2. If it beats the global best, it becomes the new incumbent
3. If not, the incumbent is also re-evaluated to account for noise

---

## 2. Tournament Opponents

Each candidate plays **5 matches** (configurable via `--matches`) against each of 11 opponents. The final fitness is the **weighted average** across all opponents.

| Opponent | Weight | Description |
|----------|--------|-------------|
| **MacDuo** | 5.0 | Strongest bot — top priority to beat |
| **Yomi** | 4.0 | Strong predictive strategy |
| **Himself** (self-play) | 3.0 | Current best TBot config as opponent |
| **MacDuoUltra** | 2.0 | MacDuo variant with advanced features |
| **Stage8** | 2.0 | Tournament-caliber opponent |
| **MecaMouse** | 1.5 | Mid-tier opponent |
| **Superhero** | 1.5 | Mid-tier opponent |
| **FifthElement** | 1.0 | Lower-tier opponent |
| **PrevRLBot** | 1.0 | Previous RL-trained bot |
| **Claude** | 1.0 | LLM-designed strategy |
| **AdaptiveKite** | 1.0 | LLM-designed kiting strategy |

**Total weight**: 22.0

Higher weights mean an opponent matters more in the fitness calculation. MacDuo (5.0) and Yomi (4.0) dominate the fitness signal, ensuring the evolved strategy prioritizes beating the strongest opponents.

**Self-play** (weight 3.0) prevents strategy cycling — the candidate must also beat the previous best version of itself.

---

## 3. Match Scoring (Simulator)

Each match is run by `HeadlessMatchRunner.java`. The simulator calculates a score for each team based on kills, damage, and survival.

### Team Composition
- **3 Main bots**: HP = 300, Speed = 1, Detection = 300
- **2 Secondary bots**: HP = 100, Speed = 3, Detection = 500

### Bot Weights
Main bots are more valuable than secondary bots:
- `MAIN_W = 1.0`
- `SEC_W = 0.75`
- `totalW = 3 × 1.0 + 2 × 0.75 = 4.5`

### Score Formula
For each team, three ratios are computed:

```
killRatio = (deadEnemyMain × 1.0 + deadEnemySec × 0.75) / 4.5

enemyDamageRatio = 1.0 - (aliveEnemyHealthWeighted / 4.5)
    where aliveEnemyHealthWeighted = Σ (botWeight × currentHP / maxHP)

survivalRatio = (aliveOwnMain × 1.0 + aliveOwnSec × 0.75) / 4.5
```

**Final score per team**:
```
score = 0.6 × killRatio + 0.3 × enemyDamageRatio + 0.1 × survivalRatio
```

| Component | Weight | What it rewards |
|-----------|--------|-----------------|
| Kill ratio | 60% | Destroying enemy bots (mains worth more) |
| Enemy damage ratio | 30% | Dealing HP damage even without kills |
| Survival ratio | 10% | Keeping your own bots alive |

**Win condition**: The team with the higher score wins. Match ends when one team is fully eliminated or timeout is reached.

---

## 4. Fitness Function (CMA-ES)

The CMA-ES fitness function (`compute_fitness`) combines multiple objectives into a single scalar:

```
fitness = 3.00 × winRate
        + 1.25 × (scoreA - scoreB)
        + 0.90 × (enemyDead - allyDead) / 5
        + 0.60 × (allyHP - enemyHP) / 1100
        + 0.25 × (1 - allyDead / 5)
        + 0.50 × speedBonus
```

Where:
```
speedBonus = winRate × max(0, 1 - avgTimeMs / timeoutMs)
```

### Component Breakdown

| Component | Weight | Range | What it rewards |
|-----------|--------|-------|-----------------|
| **Win rate** | 3.00 | [0, 3.00] | Winning matches (dominant signal) |
| **Score differential** | 1.25 | [-1.25, 1.25] | Higher simulator score than opponent |
| **Kill differential** | 0.90 | [-0.90, 0.90] | Killing more enemies than allies lost |
| **HP differential** | 0.60 | [-0.60, 0.60] | Retaining more health than opponent |
| **Survival** | 0.25 | [0, 0.25] | Keeping own bots alive |
| **Speed bonus** | 0.50 | [0, 0.50] | Winning faster (only when winning) |

### Why these weights?
- **Win rate dominates** (3.0) because the primary goal is winning
- **Score/kill/HP** provide gradient signal even in losses — a close loss is better than a total wipe
- **Speed bonus** rewards efficient victories — only applies when `winRate > 0`, scaled by how quickly the win happened relative to timeout

### Tournament Aggregation
Each candidate's final fitness is:
```
finalFitness = Σ(opponentWeight × fitnessVsOpponent) / Σ(opponentWeight)
```
This is a weighted average across all 11 opponents.

---

## 5. Theoretical Maximum Score

The absolute best a team could achieve per opponent:

| Component | Best case | Points |
|-----------|-----------|--------|
| winRate = 1.0 | Win every match | 3.00 |
| scoreA = 1.0, scoreB = 0.0 | Perfect score diff | 1.25 |
| 5 enemies dead, 0 allies dead | Kill diff = 5/5 | 0.90 |
| allyHP = max, enemyHP = 0 | HP diff = 1100/1100 | 0.60 |
| 0 allies dead | Survival = 1.0 | 0.25 |
| Instant win | speedBonus → 1.0 | 0.50 |
| | **Theoretical max** | **≈ 6.50** |

In practice, fitness values above **4.0** indicate a strong strategy, and above **5.0** indicates dominance. The speed bonus is hard to maximize because even fast victories take some time, so realistic max is closer to **6.2–6.4**.

---

## 6. The 72 Parameters

### Target Selection (10 parameters)
Controls which enemy bot to prioritize attacking.

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| STALE_TTL | 70 | [30, 200] | Ticks before an unseen enemy is marked stale (int) |
| TGT_PROXIMITY_WEIGHT | 800.0 | [200, 1500] | Weight for distance-based targeting |
| TGT_TYPE_BONUS_SEC | 200.0 | [0, 500] | Bonus priority for secondary (weaker) targets |
| TGT_LOWHP_CRIT | 350.0 | [0, 600] | HP threshold below which target gets critical priority |
| TGT_LOWHP_MOD | 180.0 | [0, 400] | Priority modifier for low-HP targets |
| TGT_FOCUS_BONUS | 400.0 | [0, 800] | Bonus for the team's focus-fire target |
| TGT_SAFEFIRE | 120.0 | [0, 300] | Bonus when target has a clear line of fire |
| TGT_STALE_PENALTY | 5.0 | [0, 20] | Per-tick penalty for stale targets |
| TGT_DMG_COMMITTED | 200.0 | [0, 500] | Bonus for targets with existing damage |
| TGT_LAST_HIT_BONUS | 150.0 | [0, 400] | Bonus for finishing off a target |

### Main Bot Utility Weights (16 parameters)
Controls Utility AI behavior scoring for the 3 main bots. Each tick, every behavior gets scored and the highest one executes.

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| U_ADVANCE_BASE | 0.4 | [0, 1] | Base score for ADVANCE behavior |
| U_ADVANCE_NO_ENEMY | 0.6 | [0, 1] | ADVANCE score boost when no enemies visible |
| U_COMBAT_HAS_TARGET | 0.8 | [0, 1.5] | COMBAT score when a target is available |
| U_COMBAT_SAFEFIRE | 0.3 | [0, 1] | COMBAT bonus when safe-fire is possible |
| U_FLANK_BLOCKED_TICKS | 0.5 | [0, 1] | FLANK score when advancing is blocked |
| U_FLANK_BLOCKED_THRESH | 20 | [5, 50] | Ticks blocked before flank activates (int) |
| U_RETREAT_HP_FACTOR | 0.7 | [0, 1] | RETREAT scales with missing HP |
| U_RETREAT_HP_THRESH | 80.0 | [20, 200] | HP threshold for retreat consideration |
| U_RETREAT_ALONE | 0.4 | [0, 1] | RETREAT bonus when alone (no allies nearby) |
| U_HOLD_AT_POS | 0.3 | [0, 1] | Score for holding position |
| U_REGROUP_SPREAD | 0.3 | [0, 1] | REGROUP score when team is too spread out |
| U_REGROUP_DIST_THRESH | 500.0 | [200, 800] | Distance threshold for regroup trigger |
| U_ANCHOR_BONUS | 0.2 | [0, 0.8] | Combat bonus for ANCHOR role |
| U_FLANKER_BONUS | 0.2 | [0, 0.8] | Flank bonus for FLANKER role |
| U_AGGRO_ALIVE_THRESH | 3 | [1, 5] | Alive allies for aggressive mode (int) |
| U_AGGRO_HP_THRESH | 200.0 | [100, 300] | HP threshold for aggressive mode |

### Secondary Bot Utility Weights (8 parameters)
Controls the 2 faster secondary bots (speed=3, HP=100).

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| US_SCOUT_BASE | 0.5 | [0, 1] | Base score for SCOUT behavior |
| US_ASSASSIN_BASE | 0.6 | [0, 1] | Base score for ASSASSIN behavior |
| US_ASSASSIN_HP_THRESH | 60.0 | [20, 100] | Enemy HP threshold for assassination |
| US_ESCORT_BASE | 0.4 | [0, 1] | Base score for ESCORT behavior |
| US_RETREAT_HP_PCT | 0.35 | [0.1, 0.6] | HP percentage for retreat |
| US_EVADE_RANGE | 250.0 | [100, 500] | Range to start evasive maneuvers |
| US_DEEP_SCOUT_X | 1600.0 | [1200, 2200] | X coordinate for deep scouting |
| US_FLANK_Y | 300.0 | [150, 600] | Y offset for flanking paths |

### Potential Field (14 parameters)
Controls force-based movement using an anti-gravity / potential field model.

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| PF_ENEMY_REPEL | 2.5 | [0.5, 5] | Repulsive force from enemies |
| PF_ENEMY_ATTRACT | 0.6 | [0.1, 2] | Attractive force toward enemies |
| PF_TANGENTIAL | 0.7 | [0.1, 2] | Tangential (orbiting) force around enemies |
| PF_ALLY_REPEL_RANGE | 200.0 | [50, 400] | Range at which allies repel each other |
| PF_ALLY_REPEL_STR | 0.9 | [0.1, 3] | Strength of ally repulsion (prevents clumping) |
| PF_WALL_STR | 0.6 | [0.1, 2] | Wall avoidance force |
| PF_WRECK_RANGE | 150.0 | [50, 300] | Range to avoid destroyed bot wrecks |
| PF_WRECK_STR | 0.5 | [0.1, 2] | Wreck avoidance strength |
| PF_FOCUS_PULL | 0.3 | [0, 1.5] | Pull toward focus-fire target |
| PF_FORMATION_PULL | 0.2 | [0, 1] | Pull toward formation position |
| PF_FLANKER_PERP | 0.4 | [0, 1.5] | Perpendicular force for flankers |
| PF_ANCHOR_CENTER | 0.3 | [0, 1] | Center-pulling force for anchor bots |
| PF_SEC_EVADE | 1.5 | [0.5, 3] | Evasion force for secondary bots |
| PF_SEC_TANGENTIAL | 1.0 | [0.1, 2] | Tangential orbiting for secondaries |

### Kiting Ranges (6 parameters)
Controls the preferred engagement distance based on tactical stance.

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| KITE_MIN_AGGRO | 300.0 | [150, 500] | Min kite range in aggressive mode |
| KITE_MAX_AGGRO | 700.0 | [400, 900] | Max kite range in aggressive mode |
| KITE_MIN_NORMAL | 360.0 | [200, 600] | Min kite range in normal mode |
| KITE_MAX_NORMAL | 780.0 | [500, 1000] | Max kite range in normal mode |
| KITE_MIN_DEFEN | 420.0 | [300, 700] | Min kite range in defensive mode |
| KITE_MAX_DEFEN | 900.0 | [600, 1200] | Max kite range in defensive mode |

### Formation & Positioning (10 parameters)
Controls team formation geometry and adaptation when bots die.

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| FORM_HOLD_X_OFFSET | 1100.0 | [600, 1600] | X offset for holding position |
| FORM_Y_SPREAD | 260.0 | [100, 500] | Y-axis spread between formation slots |
| FORM_ANCHOR_X_ADJ | 0.0 | [-200, 200] | X position adjustment for anchor bot |
| FORM_FLANKER_Y_ADJ | 100.0 | [0, 300] | Y position adjustment for flankers |
| FORM_RETREAT_X | 300.0 | [150, 500] | X coordinate for retreat position |
| FORM_REGROUP_X | 600.0 | [300, 1000] | X coordinate for regroup position |
| FORM_SEC_ESCORT_OFFSET | 150.0 | [50, 400] | Secondary bot escort offset from main |
| FORM_SEC_FLANK_Y_OFFSET | 400.0 | [200, 600] | Secondary bot Y offset for flanking |
| FORM_ADAPT_DEAD_MAIN | 0.3 | [0, 0.5] | Formation tightening per dead main |
| FORM_ADAPT_DEAD_SEC | 0.15 | [0, 0.3] | Formation tightening per dead secondary |

### Firing (4 parameters)
Controls shooting behavior and lead targeting.

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| FIRE_NOFIRE_THRESH | 20 | [5, 50] | Ticks without firing before forced shot (int) |
| FIRE_ANGLE_OFFSETS | 7 | [3, 9] | Number of angle offsets to try (spread) (int) |
| FIRE_SEC_ENGAGE_RANGE | 600.0 | [300, 900] | Max firing range for secondary bots |
| FIRE_LEAD_ACCEL_FACTOR | 0.25 | [0, 0.5] | Acceleration correction in lead targeting |

### Coordination (4 parameters)
Controls team-level communication and coordination.

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| COORD_FOCUS_RADIUS | 120.0 | [50, 300] | Radius for focus-fire agreement |
| COORD_ROLE_SWAP_CD | 100 | [30, 300] | Cooldown ticks between role swaps (int) |
| COORD_THREAT_RADIUS | 400.0 | [200, 800] | Radius for threat assessment |
| COORD_REGROUP_HP_THRESH | 150.0 | [50, 250] | Team HP threshold triggering regroup |

---

## 7. CLI Usage

### Basic Training Run
```bash
python3 train_tbot_tournament.py \
  --generations 40 \
  --pop 48 \
  --sigma0 0.80 \
  --workers 8 \
  --timeout 30000
```

### Resume from Checkpoint
```bash
python3 train_tbot_tournament.py \
  -c rl_results/tbot_run_XXXXXXXX/latest.json \
  --generations 20 \
  --sigma0 0.30
```

### All Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--generations` | 40 | Number of CMA-ES generations |
| `--pop` | 48 | Population size per generation |
| `--matches` | 5 | Matches per opponent per candidate |
| `--elite-matches` | 11 | Matches for elite re-evaluation |
| `--workers` | 4 | Parallel worker processes |
| `--timeout` | 30000 | Match timeout in milliseconds |
| `-c` / `--base-config` | None | JSON checkpoint to seed initial params |
| `--sigma0` | 0.30 | Initial step size (0.0–1.0) |
| `--sigma-min` | 0.05 | Minimum sigma before restart |
| `--sigma-restart` | 0.40 | Sigma value after restart |
| `--restart-stagnation` | 5 | Generations without improvement before restart |
| `--seed` | None | Random seed for reproducibility |

### Output Structure
```
rl_results/
  tbot_run_YYYYMMDD_HHMMSS/
    checkpoint_gen0001.json   # Per-generation checkpoint
    checkpoint_gen0002.json
    ...
    latest.json               # Always points to latest generation
    best_policy/
      teamA_best_policy.json  # Best parameters found
      TBotConfig_best.java    # Ready-to-use Java config file
logs/
  tbot-training-YYYYMMDD_HHMMSS.log  # Full training log
```

### Running a Match Manually
```bash
# In the RL-2 directory, after editing Parameters.java:
javac -cp "jars/*" -d beans --release 17 @sources.txt
java -cp "jars/*:beans" supportGUI.HeadlessMatchRunner 10 60000 1 logs
# Args: num_matches timeout_ms delay_ms log_dir
```
