# Latest benchmark report

Date: 2026-03-12 00:33 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `9052db7`

## Iteration
- Change: rebalanced secondary adherence to allied `FOCUS` calls after aggressive-focus regression.
- Targeting tweak in `AntiMacDuoV3Secondary`: focus radius `160 -> 150`, focus bonus `110 -> 85`.
- File touched: `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`

## Tournament protocol
- Runner: sub-agent `bench`
- Format: AB + BA
- Matches: 2 per leg (4 per pairing)
- Timeout: 30000 ms
- Delay: 1 ms
- Candidate:
  - `algorithms.LLMS.AntiMacDuoV3Main`
  - `algorithms.LLMS.AntiMacDuoV3Secondary`
- Baselines:
  1. `algorithms.external.MacDuoMain` + `algorithms.external.MacDuoSecondary`
  2. `algorithms.LLMS.AntiMacDuoV2Main` + `algorithms.LLMS.AntiMacDuoV2Secondary`
- Additional cross-baseline check included by bench: MacDuo vs V2.

## Results (smoke)
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.042**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.101**
- Global (candidate, 8 matches): **0W / 8L / 0D**, winrate **0%**

Cross-check baseline strength:
- MacDuo vs V2 (AB+BA, 4 matches): **4W / 0L / 0D** for MacDuo.

## Comparison with previous iteration
- Previous (commit `8c3d6fc`, aggressive focus): **0W/8L**, avg score **0.060**.
- Current (commit `9052db7`, balanced focus): **0W/8L**, avg score around **0.072** over candidate pairings.
- Net effect: small score recovery but no win recovery; candidate remains below V2 and far below MacDuo.

## Verdict
Balanced focus rollback reduces the prior over-commit damage but still fails to recover competitiveness. Keep as non-promoted experimental state.

## Artifacts
- `mini_summary.md`
- `logs/mini_tournoi_v3_cand_vs_macduo_AB/match_20260312_002605.log`
- `logs/mini_tournoi_v3_cand_vs_macduo_BA/match_20260312_002710.log`
- `logs/mini_tournoi_v3_cand_vs_v2_AB/match_20260312_002814.log`
- `logs/mini_tournoi_v3_cand_vs_v2_BA/match_20260312_002919.log`
- `logs/mini_tournoi_v3_macduo_vs_v2_AB/match_20260312_003023.log`
- `logs/mini_tournoi_v3_macduo_vs_v2_BA/match_20260312_003128.log`
