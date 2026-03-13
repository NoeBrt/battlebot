# Latest benchmark report

Date: 2026-03-13 05:58 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `86d2e1b`

## Iteration
- Change: rollback of `MAX_R` regression in `AntiMacDuoV3Main`.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Main.java`
- Parameter:
  - `MAX_R: 760 -> 720`

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
- vs MacDuo AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.000** vs **1.000**
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.189** vs **0.899**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.102** vs **0.109**
- vs AntiMacDuoV2 BA: **2W / 0L / 0D**, winrate 100.0%, avg score **0.112** vs **0.107**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.095** vs **0.949**
- vs AntiMacDuoV2 (4 matches): **2W / 2L / 0D**, winrate 50.0%, avg score **0.107** vs **0.108**

### Overall aggregate
- Total: **2W / 6L / 0D** (8 matches)
- Win rate: **25.0%**
- Avg score: candidate **0.101** vs baseline **0.529**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_054730`)
- Vs MacDuo: mixed/worse on absolute pressure (candidate avg **0.048 -> 0.095**, but baseline avg **0.719 -> 0.949**).
- Vs AntiMacDuoV2: clear improvement (candidate avg **0.100 -> 0.107**, W-L-D **0-4-0 -> 2-2-0**).
- Overall: improved from **0-8-0** to **2-6-0**.

## Verdict
Rollback from `MAX_R=760` to `MAX_R=720` recovers win capability vs V2, but MacDuo remains dominant. Keep as current best candidate band; continue targeted anti-MacDuo iteration.

## Artifacts
- `benchmark/mini_tournament_20260313_055335/summary.md`
- `benchmark/mini_tournament_20260313_055335/results.json`
- `benchmark/mini_tournament_20260313_055335/macduo_AB/match_20260313_055336.log`
- `benchmark/mini_tournament_20260313_055335/macduo_BA/match_20260313_055429.log`
- `benchmark/mini_tournament_20260313_055335/v2_AB/match_20260313_055532.log`
- `benchmark/mini_tournament_20260313_055335/v2_BA/match_20260313_055635.log`
