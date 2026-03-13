# Latest benchmark report

Date: 2026-03-13 05:11 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `2b63fcd`

## Iteration
- Change: tuned `AntiMacDuoV3Secondary` to use a more conservative staging line.
- File modified:
  - `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`
- Parameters:
  - `STAGE_X_A: 1780 -> 1650`
  - `STAGE_X_B: 1220 -> 1350`

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
- vs MacDuo BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.031** vs **0.800**
- vs AntiMacDuoV2 AB: **0W / 2L / 0D**, winrate 0.0%, avg score **0.100** vs **0.110**
- vs AntiMacDuoV2 BA: **0W / 2L / 0D**, winrate 0.0%, avg score **0.105** vs **0.107**

### Aggregated by opponent
- vs MacDuo (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.056** vs **0.679**
- vs AntiMacDuoV2 (4 matches): **0W / 4L / 0D**, winrate 0.0%, avg score **0.103** vs **0.108**

## Comparison vs previous run (`benchmark/mini_tournament_20260313_045153_8m`)
- Vs MacDuo: better (candidate avg **0.007 -> 0.056**).
- Vs AntiMacDuoV2: slightly worse (candidate avg **0.109 -> 0.103**).
- Overall: still **0 win / 8 matches**.

## Verdict
Conservative secondary staging improves resistance versus MacDuo but is still not competitive overall and regresses slightly versus V2. Keep as exploratory candidate; do not promote yet.

## Artifacts
- `benchmark/mini_tournament_20260313_050741/summary.md`
- `benchmark/mini_tournament_20260313_050741/macduo_AB/match_20260313_050742.log`
- `benchmark/mini_tournament_20260313_050741/macduo_BA/match_20260313_050846.log`
- `benchmark/mini_tournament_20260313_050741/v2_AB/match_20260313_050950.log`
- `benchmark/mini_tournament_20260313_050741/v2_BA/match_20260313_051054.log`
