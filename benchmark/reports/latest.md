# Latest benchmark report

Date: 2026-03-12 00:23 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `8c3d6fc`

## Iteration
- Change: stronger secondary adherence to allied `FOCUS` calls in `AntiMacDuoV3Secondary`.
- Targeting tweak: focus radius `140 -> 160`, focus bonus `70 -> 110`.
- File touched: `src/algorithms/LLMS/AntiMacDuoV3Secondary.java`

## Tournament protocol
- Runner: sub-agent `bench`
- Format: AB + BA
- Matches: 2 per leg
- Timeout: 30000 ms
- Delay: 1 ms
- Candidate:
  - `algorithms.LLMS.AntiMacDuoV3Main`
  - `algorithms.LLMS.AntiMacDuoV3Secondary`
- Baselines:
  1. `algorithms.external.MacDuoMain` + `algorithms.external.MacDuoSecondary`
  2. `algorithms.LLMS.AntiMacDuoV2Main` + `algorithms.LLMS.AntiMacDuoV2Secondary`

## Results (smoke)
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.016**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.105**
- Global (8 matches): **0W / 8L / 0D**, avg candidate score **0.060**

## Comparison with previous iteration
- Previous (low-HP disengage, commit `52ccbd6`): **2W/6L**, avg score **0.072**.
- Current (focus-coordination boost, commit `8c3d6fc`): **0W/8L**, avg score **0.060**.
- Net effect: clear regression.

## Verdict
Current focus-priority boost over-commits secondaries and degrades outcomes, including versus V2. Keep as failed experiment; revert or retune in next iteration.

## Artifacts
- `logs/mini_tournoi_v3_focuscoord_20260312_001815/mini_summary.md`
- `logs/mini_tournoi_v3_focuscoord_20260312_001815/vs_macduo_AB/match_20260312_001815.log`
- `logs/mini_tournoi_v3_focuscoord_20260312_001815/vs_macduo_BA/match_20260312_001919.log`
- `logs/mini_tournoi_v3_focuscoord_20260312_001815/vs_v2_AB/match_20260312_001956.log`
- `logs/mini_tournoi_v3_focuscoord_20260312_001815/vs_v2_BA/match_20260312_002059.log`
