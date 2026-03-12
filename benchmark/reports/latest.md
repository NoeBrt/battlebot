# Latest benchmark report

Date: 2026-03-12 09:35 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `2b05262`

## Iteration
- Change: secondary low-HP retreat triggers earlier in `AntiMacDuoV3Secondary`.
- Tweak: `LOW_HP_RETREAT_THRESHOLD 35 -> 45`.
- Goal: improve secondary survivability and maintain focus/broadcast utility.
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
- Candidate vs MacDuo (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.038**
- Candidate vs AntiMacDuoV2 (AB+BA, 4 matches): **0W / 4L / 0D**, avg candidate score **0.105**
- Global (candidate, 8 matches): **0W / 8L / 0D**, avg candidate score **0.072**

## Comparison with previous iteration
- Previous (`bb43e17`, distance-adaptive secondary priority): **0W/8L/0D**, avg score **0.073**.
- Current (`2b05262`, earlier low-HP retreat): **0W/8L/0D**, avg score **0.072**.
- Net effect: no outcome improvement and slight score regression.

## Verdict
Earlier low-HP retreat alone does not improve matchup quality. Keep as tested, non-promoted variant.

## Artifacts
- `logs/mini_v3_threshold/MacDuo/AB/match_20260312_093052.log`
- `logs/mini_v3_threshold/MacDuo/BA/match_20260312_093149.log`
- `logs/mini_v3_threshold/AntiMacDuoV2/AB/match_20260312_093240.log`
- `logs/mini_v3_threshold/AntiMacDuoV2/BA/match_20260312_093342.log`
