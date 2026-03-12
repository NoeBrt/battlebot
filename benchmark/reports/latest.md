# Latest benchmark report

Date: 2026-03-12 09:26 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `bb43e17`

## Iteration
- Change: made enemy-secondary priority distance-adaptive in `AntiMacDuoV3Main`.
- Tweak: for secondary targets, priority is `170` when `d < 650`, else `130`.
- Goal: reduce long-range tunnel vision while keeping strong nearby anti-secondary pressure.
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
- Note: one truncated `vs_v2_BA` log was rerun to preserve full 8-match protocol.

## Results (smoke)
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.043**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.105**
- Global (candidate, 8 matches): **0W / 8L / 0D**, avg candidate score **0.073**

## Comparison with previous iteration
- Previous (`3e7ce22`, tighter MAX_R): **0W/6L/2D**, avg score **0.061**.
- Current (`bb43e17`, distance-adaptive secondary priority): **0W/8L/0D**, avg score **0.073**.
- Net effect: higher average score but worse outcomes (lost all matches, draws disappeared).

## Verdict
Distance-adaptive secondary priority increases activity/score but hurts conversion and resilience. Keep as failed experiment; do not promote.

## Artifacts
- `logs/mini_tournoi_v3_distprio_20260312_092005/mini_summary.md`
- `logs/mini_tournoi_v3_distprio_20260312_092005/vs_macduo_AB/match_20260312_092006.log`
- `logs/mini_tournoi_v3_distprio_20260312_092005/vs_macduo_BA/match_20260312_092105.log`
- `logs/mini_tournoi_v3_distprio_20260312_092005/vs_v2_AB/match_20260312_092208.log`
- `logs/mini_tournoi_v3_distprio_20260312_092005/vs_v2_BA_rerun/match_20260312_092433.log`
