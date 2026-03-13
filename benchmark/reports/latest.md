# Latest benchmark report

Date: 2026-03-13 04:34 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `c421065`

## Iteration
- Change: tuned `algorithms.LLMS.AntiMacDuoV3Main` with low-HP close-range disengage.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`

## Tournament protocol
- Runner: sub-agent `bench`
- Format: 2 opponents × (AB + BA) × 3 matches = 12 matches total
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
- vs MacDuo AB: **0W / 3L / 0D**, winrate 0.0%, avg score **0.018** vs **1.000**
- vs MacDuo BA: **0W / 3L / 0D**, winrate 0.0%, avg score **0.029** vs **0.862**
- vs AntiMacDuoV2 AB: **0W / 2L / 1D**, winrate 0.0%, avg score **0.110** vs **0.113**
- vs AntiMacDuoV2 BA: **0W / 3L / 0D**, winrate 0.0%, avg score **0.109** vs **0.115**

### Aggregated by opponent
- vs MacDuo (6 matches): **0W / 6L / 0D**, winrate 0.0%, avg score **0.024** vs **0.931**
- vs AntiMacDuoV2 (6 matches): **0W / 5L / 1D**, winrate 0.0%, avg score **0.110** vs **0.114**

## Verdict
On this mini-sample, the low-HP retreat tweak **degrades performance** and should not be promoted.

## Artifacts
- `benchmark/mini_tournament_20260313_040331/summary.md`
- `benchmark/mini_tournament_20260313_040331/metrics.json`
- `benchmark/mini_tournament_20260313_040331/macduo_AB/match_20260313_040335.log`
- `benchmark/mini_tournament_20260313_040331/macduo_BA/match_20260313_040455.log`
- `benchmark/mini_tournament_20260313_040331/v2_AB/match_20260313_040629.log`
- `benchmark/mini_tournament_20260313_040331/v2_BA/match_20260313_040804.log`
