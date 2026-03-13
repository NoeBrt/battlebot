# Latest benchmark report

Date: 2026-03-13 10:14 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `7122ea2`

## Iteration
- Change: reduced firing spread in `AntiMacDuoV3Secondary`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameter:
  - `FIRE_SPREAD: 0.12 -> 0.10`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.652**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.100** vs **0.115**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.102** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.062** vs **0.726**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.101** vs **0.107**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.082** vs baseline **0.417**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_074415`)
- W/L profile unchanged overall and by opponent (**2-6-0**, 25% WR).
- Score deltas are negligible (candidate avg ~stable: **0.082 -> 0.082**).
- No meaningful improvement from tighter spread.

## Verdict
This spread reduction is neutral-to-slightly-negative and not worth promoting.

## Notes
- `v2_BA` required two separate 1-match executions in bench due to mid-leg interruption during 2-match invocation.

## Artifacts
- `benchmark/mini_tournament_20260313_100705/summary.md`
- `benchmark/mini_tournament_20260313_100705/results.json`
- `benchmark/mini_tournament_20260313_100705/macduo_AB/match_20260313_100706.log`
- `benchmark/mini_tournament_20260313_100705/macduo_BA/match_20260313_100809.log`
- `benchmark/mini_tournament_20260313_100705/v2_AB_rerun/match_20260313_101020.log`
- `benchmark/mini_tournament_20260313_100705/v2_BA_test1/match_20260313_101225.log`
- `benchmark/mini_tournament_20260313_100705/v2_BA_test2/match_20260313_101317.log`
