# Latest benchmark report

Date: 2026-03-12 00:37 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `a510718`

## Iteration
- Change: added a distance guardrail to `FOCUS`-driven target priority in `AntiMacDuoV3Secondary`.
- Logic: focus bonus applies only if target is both near focus point and within 780 range of current secondary position.
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
- Baseline cross-check included: MacDuo vs V2.

## Results (smoke)
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.042**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.101**
- Global (candidate, 8 matches): **0W / 8L / 0D**, avg candidate score **0.072**

Baseline cross-check:
- MacDuo vs V2 (AB+BA, 4 matches): **4W / 0L / 0D** for MacDuo.

## Comparison with previous iteration
- Previous (`9052db7`, balanced focus): **0W/8L**, avg candidate score **0.072**.
- Current (`a510718`, focus distance guardrail): **0W/8L**, avg candidate score **0.072**.
- Net effect: no measurable improvement on this smoke set.

## Verdict
Distance guardrail is neutral in current mini-tournament conditions. Keep as tested candidate but not promoted.

## Artifacts
- `mini_summary.md`
- `logs/mini_tournoi_v3_cand_vs_macduo_AB/match_20260312_002605.log`
- `logs/mini_tournoi_v3_cand_vs_macduo_BA/match_20260312_002710.log`
- `logs/mini_tournoi_v3_cand_vs_v2_AB/match_20260312_002814.log`
- `logs/mini_tournoi_v3_cand_vs_v2_BA/match_20260312_002919.log`
- `logs/mini_tournoi_v3_macduo_vs_v2_AB/match_20260312_003023.log`
- `logs/mini_tournoi_v3_macduo_vs_v2_BA/match_20260312_003128.log`
