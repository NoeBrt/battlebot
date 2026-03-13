# Latest benchmark report

Date: 2026-03-13 07:31 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `1c2f8bf`

## Iteration
- Change: rollback of tighter focus guardrail in `AntiMacDuoV3Secondary`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameter:
  - focus guardrail distance: `< 700 -> < 780`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.104** vs **0.659**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.102** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.063** vs **0.730**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.102** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.082** vs baseline **0.416**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_071815`)
- W/L profile unchanged for both opponents and overall.
- Tiny score deltas only; effectively neutral change.

## Verdict
Rollback to `<780` restores the prior performance band and is preferable to the tighter `<700` variant, but does not create a new gain.

## Artifacts
- `benchmark/mini_tournament_20260313_072719/summary.md`
- `benchmark/mini_tournament_20260313_072719/results.json`
- `benchmark/mini_tournament_20260313_072719/macduo_AB/match_20260313_072723.log`
- `benchmark/mini_tournament_20260313_072719/macduo_BA/match_20260313_072828.log`
- `benchmark/mini_tournament_20260313_072719/v2_AB/match_20260313_072931.log`
- `benchmark/mini_tournament_20260313_072719/v2_BA/match_20260313_073035.log`
