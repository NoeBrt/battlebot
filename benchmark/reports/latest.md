# Latest benchmark report

Date: 2026-03-12 00:06 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `79d2c08`

## Iteration
- Change: partial rollback of `AntiMacDuoV3Secondary` deep-scout staging.
- New staging values: `STAGE_X_A=1780`, `STAGE_X_B=1220`.
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
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.021**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **2W / 2L / 0D**, avg candidate score **0.109**
- Global (8 matches): **2W / 6L / 0D**, avg candidate score **0.065**

## Verdict
Partial rollback improves results versus V2 but remains clearly below MacDuo baseline. Keep as experimental candidate (not promoted).

## Artifacts
- `logs/mini_tournoi_rollback_v3_20260312_000046/mini_summary.md`
- `logs/mini_tournoi_rollback_v3_20260312_000046/vs_macduo_AB/match_20260312_000046.log`
- `logs/mini_tournoi_rollback_v3_20260312_000046/vs_macduo_BA/match_20260312_000150.log`
- `logs/mini_tournoi_rollback_v3_20260312_000046/vs_v2_AB/match_20260312_000240.log`
- `logs/mini_tournoi_rollback_v3_20260312_000046/vs_v2_BA/match_20260312_000344.log`
