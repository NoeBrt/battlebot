# Latest benchmark report

Date: 2026-03-12 09:42 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `0641916`

## Iteration
- Change: widened secondary clear-shot flank reposition angle in `AntiMacDuoV3Secondary`.
- Tweak: `angle = base + side * PI/3` -> `angle = base + side * PI/2.5`.
- Goal: produce more decisive lateral separation before re-engaging.
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

## Results (smoke)
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.021**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **0W / 2L / 2D**, avg candidate score **0.101**
- Global (candidate, 8 matches): **0W / 6L / 2D**, avg candidate score **0.061**

## Comparison with previous iteration
- Previous (`2b05262`, low-HP retreat threshold 45): **0W/8L/0D**, avg score **0.072**.
- Current (`0641916`, wider flank angle): **0W/6L/2D**, avg score **0.061**.
- Net effect: better match outcomes vs V2 (draws restored), but lower score average and still no wins.

## Verdict
Wider flank angle is a mild stability gain versus V2 but does not solve conversion or MacDuo matchup. Keep as experimental candidate; not promoted.

## Artifacts
- `logs/mini_tournoi_v3_flankangle_20260312_094036/mini_summary.md`
- `logs/mini_tournoi_v3_flankangle_20260312_094036/vs_macduo_AB/match_20260312_094037.log`
- `logs/mini_tournoi_v3_flankangle_20260312_094036/vs_macduo_BA/match_20260312_094140.log`
- `logs/mini_tournoi_v3_flankangle_20260312_094036/vs_v2_AB/match_20260312_094241.log`
- `logs/mini_tournoi_v3_flankangle_20260312_094036/vs_v2_BA/match_20260312_094343.log`
