# Latest benchmark report

Date: 2026-03-13 05:51 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `5e1a810`

## Iteration
- Change: expanded max engagement radius in `AntiMacDuoV3Main`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - `MAX_R: 720 -> 760`

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
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.089** vs **0.438**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.007** vs **1.000**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.100** vs **0.113**
- vs AntiMacDuoV2 BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.100** vs **0.112**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.048** vs **0.719**
- vs AntiMacDuoV2 (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.100** vs **0.112**

### Overall aggregate
- Total: **0W / 8L / 0D** (8 matches)
- Win rate: **0.0%**
- Avg score: candidate **0.074** vs baseline **0.416**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_053935`)
- Vs MacDuo: slightly worse overall (candidate avg **0.060 -> 0.048**), despite a stronger AB leg.
- Vs AntiMacDuoV2: worse (candidate avg **0.102 -> 0.100**, and lost BA wins).
- Overall: regressed from **2-6-0** to **0-8-0**.

## Verdict
Increasing `MAX_R` to 760 is a regression in this sample. Revert this change or test a narrower range around 720.

## Artifacts
- `benchmark/mini_tournament_20260313_054730/summary.md`
- `benchmark/mini_tournament_20260313_054730/macduo_AB/match_20260313_054732.log`
- `benchmark/mini_tournament_20260313_054730/macduo_BA/match_20260313_054836.log`
- `benchmark/mini_tournament_20260313_054730/v2_AB/match_20260313_054914.log`
- `benchmark/mini_tournament_20260313_054730/v2_BA/match_20260313_055018.log`
