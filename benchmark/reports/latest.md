# Latest benchmark report

Date: 2026-03-12 00:15 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `52ccbd6`

## Iteration
- Change: added low-HP disengage behavior in `AntiMacDuoV3Secondary` skirmish logic.
- Goal: preserve secondary survival and keep focus/broadcast utility longer.
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
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.035**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **2W / 2L / 0D**, avg candidate score **0.109**
- Global (8 matches): **2W / 6L / 0D**, avg candidate score **0.072**

## Comparison with previous iteration
- Previous (partial rollback only, commit `79d2c08`): global avg score **0.065**.
- Current (low-HP disengage, commit `52ccbd6`): global avg score **0.072**.
- Net effect: slight improvement in average score, same win/loss profile (still 2W/6L).

## Verdict
Low-HP disengage is a mild positive tweak but insufficient versus MacDuo. Keep as experimental branch behavior; continue with additional anti-MacDuo adaptations.

## Artifacts
- `logs/mini_tournoi_v3_lowhp_20260312_000956/mini_summary.md`
- `logs/mini_tournoi_v3_lowhp_20260312_000956/vs_macduo_AB/match_20260312_000957.log`
- `logs/mini_tournoi_v3_lowhp_20260312_000956/vs_macduo_BA/match_20260312_001101.log`
- `logs/mini_tournoi_v3_lowhp_20260312_000956/vs_v2_AB/match_20260312_001204.log`
- `logs/mini_tournoi_v3_lowhp_20260312_000956/vs_v2_BA/match_20260312_001308.log`
