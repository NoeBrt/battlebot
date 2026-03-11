# Latest benchmark report

Date: 2026-03-11 23:57 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `7b18add`

## Iteration
- Change: `AntiMacDuoV3Secondary` staging depth increased (`STAGE_X_A=2100`, `STAGE_X_B=900`) to push scouts deeper and provide earlier target cues.
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
- Candidate vs MacDuo (AB+BA, 4 matches): **0 win / 4 losses**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **0 win / 4 losses**
- Global: **0/8 wins**

## Verdict
Deep-scout staging regression on this smoke set. Keep as failed experimental candidate; do not promote.

## Artifacts
- `benchmark_tmp/mini_tournament_20260311_235143_antimacduov3_deepscout/mini_summary.md`
- `benchmark_tmp/mini_tournament_20260311_235143_antimacduov3_deepscout/cand_vs_macduo_AB/match_20260311_235143.log`
- `benchmark_tmp/mini_tournament_20260311_235143_antimacduov3_deepscout/cand_vs_macduo_BA/match_20260311_235246.log`
- `benchmark_tmp/mini_tournament_20260311_235143_antimacduov3_deepscout/cand_vs_v2_AB/match_20260311_235339.log`
- `benchmark_tmp/mini_tournament_20260311_235143_antimacduov3_deepscout/cand_vs_v2_BA/match_20260311_235442.log`
