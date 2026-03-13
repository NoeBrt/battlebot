# Latest benchmark report

Date: 2026-03-13 07:41 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `4e8cf3e`

## Iteration
- Change: narrowed `AntiMacDuoV3Main` fire fan.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter logic:
  - fire angles changed from 5 probes (`base`, `±spread`, `±2*spread`) to 3 probes (`base`, `±spread`).

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.032** vs **0.853**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.100** vs **0.110**
- vs AntiMacDuoV2 BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.103** vs **0.118**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.027** vs **0.827**
- vs AntiMacDuoV2 (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.102** vs **0.114**

### Overall aggregate
- Total: **0W / 8L / 0D** (8 matches)
- Win rate: **0.0%**
- Avg score: candidate **0.064** vs baseline **0.470**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_072719`)
- Overall W-L-D regressed from **2-6-0** to **0-8-0**.
- Candidate average score dropped (**0.082 -> 0.064**).
- Lost prior wins vs V2 (from **2-2-0** to **0-4-0**).

## Verdict
Narrowing the fire fan is a clear regression and should be rejected.

## Artifacts
- `benchmark/mini_tournament_20260313_073603/summary.md`
- `benchmark/mini_tournament_20260313_073603/results.json`
- `benchmark/mini_tournament_20260313_073603/macduo_AB/match_20260313_073605.log`
- `benchmark/mini_tournament_20260313_073603/macduo_BA/match_20260313_073709.log`
- `benchmark/mini_tournament_20260313_073603/v2_AB/match_20260313_073812.log`
- `benchmark/mini_tournament_20260313_073603/v2_BA/match_20260313_073915.log`
