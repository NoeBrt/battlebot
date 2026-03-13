# Latest benchmark report

Date: 2026-03-13 07:14 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `619be7d`

## Iteration
- Change: rollback of M3 pressure-bonus regression in `AntiMacDuoV3Main`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - `M3 main-target bonus: +55 -> +45`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.191** vs **0.916**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.109**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.107** vs **0.105**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.096** vs **0.958**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.105** vs **0.107**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.100** vs baseline **0.533**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_065925`)
- Vs MacDuo: same W/L, candidate score up but baseline score up sharply too.
- Vs AntiMacDuoV2: recovered from **0-2-2** to **2-2-0**.
- Overall: recovered from **0-6-2** to **2-6-0** (win rate **0.0% -> 25.0%**).

## Verdict
Rollback restores prior win capability vs V2 and is preferable to the +55 variant, but does not solve the core weakness against MacDuo.

## Artifacts
- `benchmark/mini_tournament_20260313_070859/summary.md`
- `benchmark/mini_tournament_20260313_070859/results.json`
- `benchmark/mini_tournament_20260313_070859/macduo_AB/match_20260313_070900.log`
- `benchmark/mini_tournament_20260313_070859/macduo_BA/match_20260313_070951.log`
- `benchmark/mini_tournament_20260313_070859/v2_AB/match_20260313_071053.log`
- `benchmark/mini_tournament_20260313_070859/v2_BA/match_20260313_071157.log`
