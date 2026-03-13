# Latest benchmark report

Date: 2026-03-13 07:05 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `c7d8740`

## Iteration
- Change: increased M3 pressure bonus against main targets.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - `M3 main-target bonus: +45 -> +55`

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
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.022** vs **0.800**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.104** vs **0.654**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.101** vs **0.105**
- vs AntiMacDuoV2 BA: **0W / 0L / 2D**, winrate 0.0%, avg score **0.100** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.063** vs **0.727**
- vs AntiMacDuoV2 (4 matches): **0W / 2L / 2D**, winrate 0.0%, avg score **0.101** vs **0.103**

### Overall aggregate
- Total: **0W / 6L / 2D** (8 matches)
- Win rate: **0.0%**
- Avg score: candidate **0.082** vs baseline **0.415**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_064537`)
- Vs MacDuo: essentially unchanged (still **0-4-0**).
- Vs AntiMacDuoV2: regressed from **2-2-0** to **0-2-2**.
- Overall: regressed from **2-6-0** to **0-6-2**, with near-identical average score.

## Verdict
Increasing M3 main-target pressure bonus is not beneficial and should be rejected.

## Artifacts
- `benchmark/mini_tournament_20260313_065925/summary.md`
- `benchmark/mini_tournament_20260313_065925/results.json`
- `benchmark/mini_tournament_20260313_065925/macduo_AB/match_20260313_065927.log`
- `benchmark/mini_tournament_20260313_065925/macduo_BA/match_20260313_070031.log`
- `benchmark/mini_tournament_20260313_065925/v2_AB/match_20260313_070135.log`
- `benchmark/mini_tournament_20260313_065925/v2_BA/match_20260313_070238.log`
