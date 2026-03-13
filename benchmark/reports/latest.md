# Latest benchmark report

Date: 2026-03-13 05:29 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `34008ff`

## Iteration
- Change: widened max engagement radius in `AntiMacDuoV3Main` for safer kiting.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - `MAX_R: 680 -> 720`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.095** vs **0.651**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.105**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.104** vs **0.100**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.058** vs **0.726**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.103** vs **0.103**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.081** vs baseline **0.414**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_051502`)
- Vs MacDuo: slightly better (candidate avg **0.049 -> 0.058**).
- Vs AntiMacDuoV2: essentially unchanged (score parity maintained, 2W/2L).
- Overall W-L-D: unchanged at **2-6-0**.

## Verdict
Small gain versus MacDuo with no overall win-rate improvement. Candidate is more stable but still not competitive enough to promote.

## Artifacts
- `benchmark/mini_tournament_20260313_052411/summary.md`
- `benchmark/mini_tournament_20260313_052411/macduo_AB/match_20260313_052412.log`
- `benchmark/mini_tournament_20260313_052411/macduo_BA/match_20260313_052516.log`
- `benchmark/mini_tournament_20260313_052411/v2_AB/match_20260313_052620.log`
- `benchmark/mini_tournament_20260313_052411/v2_BA/match_20260313_052723.log`
