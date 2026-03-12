# Latest benchmark report

Date: 2026-03-12 09:10 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `51d14b2`

## Iteration
- Change: increased enemy-secondary target priority in `AntiMacDuoV3Main`.
- Tweak: secondary priority `140 -> 170` in target scoring.
- Goal: apply stronger pressure on enemy support units (especially vs MacDuo).
- File touched: `src/algorithms/LLMS/AntiMacDuoV3Main.java`

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
- Previous (`269474a`, main kiting MIN_R=360): **1W/5L/2D**, avg score **0.060**.
- Current (`51d14b2`, secondary-priority boost): **0W/6L/2D**, avg score **0.061**.
- Net effect: slight score uptick, but no wins and no improvement vs MacDuo.

## Verdict
Higher secondary-priority alone is insufficient. Keeps V3 marginally stable vs V2 (draws) but does not convert into wins and remains weak vs MacDuo.

## Artifacts
- `logs/mini_tournoi_v3_secondaryprio_20260312_090618/mini_summary.md`
- `logs/mini_tournoi_v3_secondaryprio_20260312_090618/vs_macduo_AB/match_20260312_090618.log`
- `logs/mini_tournoi_v3_secondaryprio_20260312_090618/vs_macduo_BA/match_20260312_090651.log`
- `logs/mini_tournoi_v3_secondaryprio_20260312_090618/vs_v2_AB/match_20260312_090748.log`
- `logs/mini_tournoi_v3_secondaryprio_20260312_090618/vs_v2_BA/match_20260312_090850.log`
