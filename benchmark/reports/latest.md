# Latest benchmark report

Date: 2026-03-13 06:50 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `9fb46da`

## Iteration
- Change: rollback of earlier flanking trigger in `AntiMacDuoV3Main`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - flanking trigger: `noFireTicks > 22 -> > 25`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.101** vs **0.652**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.104** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.061** vs **0.726**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.103** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.082** vs baseline **0.414**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_063702`)
- W/L profile unchanged overall and by opponent.
- Only tiny score variations (effectively neutral): overall candidate avg **0.083 -> 0.082**.

## Verdict
Rollback to the stable flanking threshold recovers consistency but does not materially improve performance. Candidate remains in the same performance band.

## Artifacts
- `benchmark/mini_tournament_20260313_064537/summary.md`
- `benchmark/mini_tournament_20260313_064537/results.json`
- `benchmark/mini_tournament_20260313_064537/macduo_AB/match_20260313_064538.log`
- `benchmark/mini_tournament_20260313_064537/macduo_BA/match_20260313_064642.log`
- `benchmark/mini_tournament_20260313_064537/v2_AB/match_20260313_064746.log`
- `benchmark/mini_tournament_20260313_064537/v2_BA/match_20260313_064849.log`
