# Latest benchmark report

Date: 2026-03-13 06:25 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `4a84f16`

## Iteration
- Change: tightened low-HP retreat trigger on `AntiMacDuoV3Secondary`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameters:
  - `LOW_HP_RETREAT_THRESHOLD: 45 -> 40`
  - `LOW_HP_RETREAT_RANGE: 520 -> 500`

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
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.022** vs **0.816**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.005** vs **1.000**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.102** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.013** vs **0.908**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.102** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.058** vs baseline **0.505**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_061151`)
- Vs MacDuo: strong regression (candidate avg **0.063 -> 0.013**).
- Vs AntiMacDuoV2: unchanged (aggregate still **2W / 2L**).
- Overall: same W-L-D (**2-6-0**) but much lower average score (**0.082 -> 0.058**).

## Verdict
This low-HP retreat tightening does not improve outcomes and significantly hurts MacDuo matchup quality. Do not promote.

## Artifacts
- `benchmark/mini_tournament_20260313_062203/summary.md`
- `benchmark/mini_tournament_20260313_062203/results.json`
- `benchmark/mini_tournament_20260313_062203/macduo_AB/match_20260313_062205.log`
- `benchmark/mini_tournament_20260313_062203/macduo_BA/match_20260313_062309.log`
- `benchmark/mini_tournament_20260313_062203/v2_AB/match_20260313_062349.log`
- `benchmark/mini_tournament_20260313_062203/v2_BA/match_20260313_062452.log`
