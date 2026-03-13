# Latest benchmark report

Date: 2026-03-13 05:44 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `7c5c11f`

## Iteration
- Change: rollback of the early flank trigger regression in `AntiMacDuoV3Secondary`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameter:
  - `noFireTicks` flank trigger: `>16 -> >20`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.098** vs **0.650**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.102** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.060** vs **0.725**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.102** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.081** vs baseline **0.414**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_053144`)
- Vs MacDuo: strong recovery (candidate avg **0.019 -> 0.060**).
- Vs AntiMacDuoV2: improved (candidate avg **0.100 -> 0.102**, and 2 wins restored in BA leg).
- Overall: recovered from **0-6-2** to **2-6-0**.

## Verdict
Rollback successfully removed the regression and restores the previous performance band. Candidate remains promising vs V2 but still non-competitive vs MacDuo.

## Artifacts
- `benchmark/mini_tournament_20260313_053935/summary.md`
- `benchmark/mini_tournament_20260313_053935/macduo_AB/match_20260313_053937.log`
- `benchmark/mini_tournament_20260313_053935/macduo_BA/match_20260313_054040.log`
- `benchmark/mini_tournament_20260313_053935/v2_AB/match_20260313_054144.log`
- `benchmark/mini_tournament_20260313_053935/v2_BA/match_20260313_054247.log`
