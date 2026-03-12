# Latest benchmark report

Date: 2026-03-12 09:02 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `269474a`

## Iteration
- Change: widened V3 main kiting bubble by increasing `MIN_R` from `320` to `360` in `AntiMacDuoV3Main`.
- Goal: reduce close-range trades and survive longer in main-bot duels.
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
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.014**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **1W / 1L / 2D**, avg candidate score **0.105**
- Global (candidate, 8 matches): **1W / 5L / 2D**, avg candidate score **0.060**

## Comparison with previous iteration
- Previous (`a510718`, focus-distance guardrail): **0W/8L/0D**, avg candidate score **0.072**.
- Current (`269474a`, main MIN_R increased): **1W/5L/2D**, avg candidate score **0.060**.
- Net effect: better W/L profile thanks to draws and one win vs V2, but weaker average score and still dominated by MacDuo.

## Verdict
Main kiting radius increase improves stability against V2 but does not address MacDuo matchup. Keep as experimental candidate; combine with stronger anti-MacDuo targeting/positioning changes next.

## Artifacts
- `logs/mini_tournoi_main_kiting_20260312_085757/mini_summary.md`
- `logs/mini_tournoi_main_kiting_20260312_085757/vs_macduo_AB/match_20260312_085758.log`
- `logs/mini_tournoi_main_kiting_20260312_085757/vs_macduo_BA/match_20260312_085828.log`
- `logs/mini_tournoi_main_kiting_20260312_085757/vs_v2_AB/match_20260312_085925.log`
- `logs/mini_tournoi_main_kiting_20260312_085757/vs_v2_BA/match_20260312_090027.log`
