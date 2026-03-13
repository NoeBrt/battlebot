# Latest benchmark report

Date: 2026-03-13 06:42 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `c5e67e7`

## Iteration
- Change: trigger main flanking slightly earlier when firing stalls.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - flanking trigger: `noFireTicks > 25 -> > 22`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.104** vs **0.655**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.102** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.063** vs **0.728**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.102** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.083** vs baseline **0.415**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_062859`)
- Win/loss profile unchanged: still **2-6-0** overall.
- Candidate average dropped (**0.106 -> 0.083**), with regressions on both opponents.
- No evidence of improvement from earlier flanking trigger.

## Verdict
This tuning is not an improvement and should be rejected.

## Artifacts
- `benchmark/mini_tournament_20260313_063702/summary.md`
- `benchmark/mini_tournament_20260313_063702/results.json`
- `benchmark/mini_tournament_20260313_063702/macduo_AB/match_20260313_063703.log`
- `benchmark/mini_tournament_20260313_063702/macduo_BA/match_20260313_063807.log`
- `benchmark/mini_tournament_20260313_063702/v2_AB/match_20260313_063910.log`
- `benchmark/mini_tournament_20260313_063702/v2_BA/match_20260313_064014.log`
