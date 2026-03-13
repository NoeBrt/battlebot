# Latest benchmark report

Date: 2026-03-13 04:56 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `fdc9b5c`

## Iteration
- Change: rollback of the low-HP retreat tweak in `AntiMacDuoV3Main`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`

## Tournament protocol
- Runner: sub-agent `bench`
- Format: 2 opponents × (AB + BA) × 2 matches = 8 matches total
- Timeout: 30000 ms
- Delay: 1 ms
- Candidate:
  - `algorithms.LLMS.AntiMacDuoV3Main`
  - `algorithms.LLMS.AntiMacDuoV3Secondary`
- Baselines:
  - `algorithms.external.MacDuoMain` + `algorithms.external.MacDuoSecondary`
  - `algorithms.LLMS.AntiMacDuoV2Main` + `algorithms.LLMS.AntiMacDuoV2Secondary`

## Results (candidate perspective)

### Per leg
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.009** vs **1.000**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.005** vs **1.000**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.108** vs **0.113**
- vs AntiMacDuoV2 BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.109** vs **0.115**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.007** vs **1.000**
- vs AntiMacDuoV2 (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.109** vs **0.114**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_040331`)
- Vs MacDuo: worse (**0.024 → 0.007** candidate avg).
- Vs AntiMacDuoV2: flat/slightly worse (**0.110 → 0.109** candidate avg).
- Overall: rollback did **not** improve performance.

## Verdict
This rollback candidate is not competitive versus current baselines and should not be promoted.

## Artifacts
- `benchmark/mini_tournament_20260313_045153_8m/summary.md`
- `benchmark/mini_tournament_20260313_045153_8m/macduo_AB/match_20260313_045155.log`
- `benchmark/mini_tournament_20260313_045153_8m/macduo_BA/match_20260313_045254.log`
- `benchmark/mini_tournament_20260313_045153_8m/v2_AB/match_20260313_045336.log`
- `benchmark/mini_tournament_20260313_045153_8m/v2_BA/match_20260313_045440.log`
