# Latest benchmark report

Date: 2026-03-13 05:36 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `44a885e`

## Iteration
- Change: `AntiMacDuoV3Secondary` now triggers FLANKING reposition sooner when no shots are fired.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameter:
  - `noFireTicks` flank trigger: `>20 -> >16`

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
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.000** vs **1.000**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.039** vs **0.860**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.100** vs **0.110**
- vs AntiMacDuoV2 BA: **0W / 0L / 2D**, winrate 0.0%, avg score **0.100** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.019** vs **0.930**
- vs AntiMacDuoV2 (4 matches): **0W / 2L / 2D**, winrate 0.0%, avg score **0.100** vs **0.105**

### Overall aggregate
- Total: **0W / 6L / 2D** (8 matches)
- Win rate: **0.0%**
- Avg score: candidate **0.060** vs baseline **0.517**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_052411`)
- Vs MacDuo: significantly worse (candidate avg **0.058 -> 0.019**).
- Vs AntiMacDuoV2: slightly worse (candidate avg **0.103 -> 0.100**).
- Overall: regressed from **2-6-0** to **0-6-2**.

## Verdict
This tweak is a regression and should be rolled back or replaced in the next iteration.

## Artifacts
- `benchmark/mini_tournament_20260313_053144/summary.md`
- `benchmark/mini_tournament_20260313_053144/macduo_AB/match_20260313_053146.log`
- `benchmark/mini_tournament_20260313_053144/macduo_BA/match_20260313_053220.log`
- `benchmark/mini_tournament_20260313_053144/v2_AB/match_20260313_053324.log`
- `benchmark/mini_tournament_20260313_053144/v2_BA/match_20260313_053427.log`
