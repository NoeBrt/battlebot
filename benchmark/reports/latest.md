# Latest benchmark report

Date: 2026-03-13 04:09 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `c421065`

## Iteration
- Change: tuned `AntiMacDuoV3Main` with low-HP close-range disengage to preserve main bots.
- File changed:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`

## Tournament protocol
- Runner: sub-agent `bench`
- Format: 2 baselines × (AB + BA) × 3 matches = 12 matches
- Timeout: 30000 ms
- Delay: 1 ms
- Candidate team:
  - `algorithms.LLMS.AntiMacDuoV3Main` + `algorithms.LLMS.AntiMacDuoV3Secondary`
- Baselines:
  1. `algorithms.external.MacDuoMain` + `algorithms.external.MacDuoSecondary`
  2. `algorithms.LLMS.AntiMacDuoV2Main` + `algorithms.LLMS.AntiMacDuoV2Secondary`

## Results (candidate perspective)

### Per leg
- vs MacDuo AB: **0W / 3L / 0D**, avg score cand/base = **0.018 / 1.000**
- vs MacDuo BA: **0W / 3L / 0D**, avg score cand/base = **0.029 / 0.862**
- vs V2 AB: **0W / 2L / 1D**, avg score cand/base = **0.110 / 0.113**
- vs V2 BA: **0W / 3L / 0D**, avg score cand/base = **0.109 / 0.115**

### Aggregated by opponent
- vs `MacDuo`: **0W / 6L / 0D**, win rate **0.0%**, avg score **0.024** vs **0.931**
- vs `AntiMacDuoV2`: **0W / 5L / 1D**, win rate **0.0%**, avg score **0.110** vs **0.114**

## Verdict
The low-HP retreat tweak appears **degraded** on this 12-match sample:
- no wins against either baseline,
- score gap widened heavily vs MacDuo,
- still slightly behind V2 overall.

Recommendation: rollback or narrow retreat trigger (HP/range) and prioritize shot conversion improvements over disengage depth.

## Artifacts
- `benchmark/mini_tournament_20260313_040331/summary.md`
- `benchmark/mini_tournament_20260313_040331/metrics.json`
- `benchmark/mini_tournament_20260313_040331/macduo_AB/match_20260313_040335.log`
- `benchmark/mini_tournament_20260313_040331/macduo_BA/match_20260313_040455.log`
- `benchmark/mini_tournament_20260313_040331/v2_AB/match_20260313_040629.log`
- `benchmark/mini_tournament_20260313_040331/v2_BA/match_20260313_040804.log`
