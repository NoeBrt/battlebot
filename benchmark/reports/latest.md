# Latest benchmark report

Date: 2026-03-13 06:08 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `54226e7`

## Iteration
- Change: constrained focus-bonus application in `AntiMacDuoV3Main` to practical engagement distance.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter logic:
  - Focus bonus (`+80`) now applies only when `distance_to_enemy < 720`.

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.047** vs **0.622**
- vs AntiMacDuoV2 AB: **0W / 0L / 2D**, winrate 0.0%, avg score **0.100** vs **0.100**
- vs AntiMacDuoV2 BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.100** vs **0.105**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.035** vs **0.711**
- vs AntiMacDuoV2 (4 matches): **0W / 2L / 2D**, winrate 0.0%, avg score **0.100** vs **0.103**

### Overall aggregate
- Total: **0W / 6L / 2D** (8 matches)
- Win rate: **0.0%**
- Avg score: candidate **0.067** vs baseline **0.407**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_055335`)
- Vs MacDuo: candidate average dropped (**0.095 -> 0.035**), despite lower baseline average too.
- Vs AntiMacDuoV2: regressed from split wins (**2-2-0 -> 0-2-2**).
- Overall: regressed from **2-6-0** to **0-6-2** (win rate **25.0% -> 0.0%**).

## Verdict
This focus-distance gating tweak is a regression and should not be promoted.

## Artifacts
- `benchmark/mini_tournament_20260313_060319/summary.md`
- `benchmark/mini_tournament_20260313_060319/results.json`
- `benchmark/mini_tournament_20260313_060319/macduo_AB/match_20260313_060321.log`
- `benchmark/mini_tournament_20260313_060319/macduo_BA/match_20260313_060424.log`
- `benchmark/mini_tournament_20260313_060319/v2_AB/match_20260313_060528.log`
- `benchmark/mini_tournament_20260313_060319/v2_BA/match_20260313_060632.log`
