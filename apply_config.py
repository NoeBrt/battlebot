import json
import sys
import math
from pathlib import Path

# Copied from rl_train_vs_macduo.py to ensure consistency
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

def load_and_apply_config(config_path, output_path):
    print(f"Loading config from {config_path}")
    with open(config_path) as f:
        data = json.load(f)

    # Handle different JSON structures:
    # 1. Direct dict of params (e.g. from best_to_start.json "paramsA": {...})
    # 2. Wrapper with "paramsA": {...}
    
    if "paramsA" in data:
        params_dict = data["paramsA"]
    else:
        # Assume it's a flat dict of params, verify against PARAM_SPEC
        first_param = PARAM_SPEC[0][0]
        if first_param in data:
            params_dict = data
        else:
             print(f"Error: Could not find parameters in {config_path}. Expected 'paramsA' key or direct parameters.")
             sys.exit(1)

    lines = [
        "package algorithms.rl;",
        "",
        "public class RLConfig {",
        "    // Generated from " + str(config_path),
    ]

    for (name, default_val, lo, hi, is_int) in PARAM_SPEC:
        val = params_dict.get(name, default_val)
        # Ensure value is clamped within bounds, just in case
        val = max(lo, min(hi, float(val)))
        
        if is_int:
            lines.append(f"    public static final int    {name} = {int(round(val))};")
        else:
            lines.append(f"    public static final double {name} = {val:.6f};")

    lines.append("")
    lines.append("    // Fixed constants")
    for name, val in FIXED_CONSTANTS.items():
        if isinstance(val, int):
            lines.append(f"    public static final int    {name} = {val};")
        else:
            lines.append(f"    public static final double {name} = {float(val):.6f};")

    lines.append("}")
    lines.append("")
    
    with open(output_path, "w") as f:
        f.write("\n".join(lines))
    
    print(f"Updated {output_path}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 apply_config.py <config.json> [output_path]")
        sys.exit(1)
    
    config_file = sys.argv[1]
    
    if len(sys.argv) >= 3:
        out_file = sys.argv[2]
    else:
        # Default to src/algorithms/rl/RLConfig.java
        out_file = "src/algorithms/rl/RLConfig.java"

    load_and_apply_config(config_file, out_file)
