# Latest benchmark report

Date: 2026-03-13 07:23 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `9915ffc`

## Iteration
- Change: tightened focus-call distance guardrail in `AntiMacDuoV3Secondary`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameter:
  - focus guardrail distance: `< 780 -> < 700`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.099** vs **0.649**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.102** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.060** vs **0.725**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.102** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.081** vs baseline **0.414**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_070859`)
- W/L profile unchanged overall and by opponent.
- Candidate average score decreased overall (**0.100 -> 0.081**).
- No measurable upside from tighter focus guardrail.

## Verdict
This guardrail tightening does not improve competitiveness and should not be promoted.

## Artifacts
- `benchmark/mini_tournament_20260313_071815/summary.md`
- `benchmark/mini_tournament_20260313_071815/results.json`
- `benchmark/mini_tournament_20260313_071815/macduo_AB/match_20260313_071817.log`
- `benchmark/mini_tournament_20260313_071815/macduo_BA/match_20260313_071921.log`
- `benchmark/mini_tournament_20260313_071815/v2_AB/match_20260313_072025.log`
- `benchmark/mini_tournament_20260313_071815/v2_BA/match_20260313_072128.log`
