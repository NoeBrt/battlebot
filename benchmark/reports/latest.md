# Latest benchmark report

Date: 2026-03-13 06:32 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `fa4e33e`

## Iteration
- Change: rollback of low-HP retreat tightening in `AntiMacDuoV3Secondary`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameters:
  - `LOW_HP_RETREAT_THRESHOLD: 40 -> 45`
  - `LOW_HP_RETREAT_RANGE: 500 -> 520`

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
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.011** vs **0.900**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.201** vs **0.848**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.109**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.112** vs **0.106**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.106** vs **0.874**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.107** vs **0.107**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.106** vs baseline **0.491**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_062203`)
- Vs MacDuo: large score recovery (candidate avg **0.013 -> 0.106**), still 0 wins.
- Vs AntiMacDuoV2: slightly better candidate avg (**0.102 -> 0.107**) with same 2W/2L split.
- Overall: same W-L-D (**2-6-0**), but much better candidate average (**0.058 -> 0.106**).

## Verdict
Rollback successfully removes the previous degradation and restores the stronger score profile. Still no breakthrough vs MacDuo, but this is a better operating point than the tightened retreat variant.

## Artifacts
- `benchmark/mini_tournament_20260313_062859/summary.md`
- `benchmark/mini_tournament_20260313_062859/results.json`
- `benchmark/mini_tournament_20260313_062859/macduo_AB/match_20260313_062900.log`
- `benchmark/mini_tournament_20260313_062859/macduo_BA/match_20260313_062954.log`
- `benchmark/mini_tournament_20260313_062859/v2_AB/match_20260313_063058.log`
- `benchmark/mini_tournament_20260313_062859/v2_BA/match_20260313_063202.log`
