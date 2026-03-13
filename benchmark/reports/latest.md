# Latest benchmark report

Date: 2026-03-13 10:02 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `eebb201`

## Iteration
- Change: rollback of narrowed fire fan in `AntiMacDuoV3Main`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter logic:
  - fire angles restored from 3 probes back to 5 probes (`base`, `±spread`, `±2*spread`).

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.104** vs **0.658**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.102** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.063** vs **0.729**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.102** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.082** vs baseline **0.416**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_073603`)
- Overall improved from **0-8-0** to **2-6-0**.
- Candidate average score increased (**0.064 -> 0.082**).
- Baseline average score decreased (**0.470 -> 0.416**).

## Verdict
Rollback of the narrowed fire fan recovers the previous performance band and should be preferred over the 3-angle variant.

## Artifacts
- `benchmark/mini_tournament_20260313_074415/summary.md`
- `benchmark/mini_tournament_20260313_074415/results.json`
- `benchmark/mini_tournament_20260313_074415/macduo_AB/match_20260313_074417.log`
- `benchmark/mini_tournament_20260313_074415/macduo_BA/match_20260313_074521.log`
- `benchmark/mini_tournament_20260313_074415/v2_AB/match_20260313_074624.log`
- `benchmark/mini_tournament_20260313_074415/v2_BA/match_20260313_074728.log`
