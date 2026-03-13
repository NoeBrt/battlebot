# Latest benchmark report

Date: 2026-03-13 05:19 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `5b56784`

## Iteration
- Change: tuned `AntiMacDuoV3Main` by lowering long-range secondary priority.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - long-range secondary priority: `130 -> 120`

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
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.081** vs **0.558**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.017** vs **1.000**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.105** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.049** vs **0.779**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.103** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.076** vs baseline **0.441**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_050741`)
- Vs MacDuo: slightly worse (candidate avg **0.056 -> 0.049**).
- Vs AntiMacDuoV2: improved (from **0W/4L** to **2W/2L**, avg scores now tied).
- Overall: first wins observed in this sequence, but still weak against MacDuo.

## Verdict
This tuning is a partial improvement (breakthrough vs V2) but remains non-competitive vs MacDuo. Keep as candidate for further iteration; not promotion-ready.

## Artifacts
- `benchmark/mini_tournament_20260313_051502/summary.md`
- `benchmark/mini_tournament_20260313_051502/macduo_AB/match_20260313_051504.log`
- `benchmark/mini_tournament_20260313_051502/macduo_BA/match_20260313_051608.log`
- `benchmark/mini_tournament_20260313_051502/v2_AB/match_20260313_051711.log`
- `benchmark/mini_tournament_20260313_051502/v2_BA/match_20260313_051815.log`
