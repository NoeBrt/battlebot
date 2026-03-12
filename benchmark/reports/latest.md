# Latest benchmark report

Date: 2026-03-12 09:16 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `3e7ce22`

## Iteration
- Change: tightened V3 main maximum kiting range (`MAX_R 720 -> 680`) while keeping `MIN_R=360`.
- Goal: reduce over-kiting and force more meaningful engagements.
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
- Previous (`51d14b2`, higher secondary target priority): **0W/6L/2D**, avg score **0.061**.
- Current (`3e7ce22`, tighter MAX_R): **0W/6L/2D**, avg score **0.061**.
- Net effect: no measurable change on this smoke set.

## Verdict
Tightening max kiting range alone is neutral under current conditions. Keep as tested but non-promoted candidate.

## Artifacts
- `logs/mini_tournoi_v3_tightermaxr_20260312_091223/mini_summary.md`
- `logs/mini_tournoi_v3_tightermaxr_20260312_091223/vs_macduo_AB/match_20260312_091223.log`
- `logs/mini_tournoi_v3_tightermaxr_20260312_091223/vs_macduo_BA/match_20260312_091256.log`
- `logs/mini_tournoi_v3_tightermaxr_20260312_091223/vs_v2_AB/match_20260312_091353.log`
- `logs/mini_tournoi_v3_tightermaxr_20260312_091223/vs_v2_BA/match_20260312_091455.log`
