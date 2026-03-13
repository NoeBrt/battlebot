# Mini Tournament Summary (candidate: AntiMacDuoV3)

- Root artifact dir: `benchmark/mini_tournament_20260313_040331`
- Format: 2 opponents × (AB + BA) × 3 matches, timeout=30000ms, delay=1ms

## Per-leg metrics (candidate perspective)

| Opponent | Leg | W-L-D | Win rate | Avg score (candidate) | Avg score (baseline) | Log |
|---|---:|---:|---:|---:|---:|---|
| MacDuo | AB | 0-3-0 | 0.0% | 0.018 | 1.000 | `benchmark/mini_tournament_20260313_040331/macduo_AB/match_20260313_040335.log` |
| MacDuo | BA | 0-3-0 | 0.0% | 0.029 | 0.862 | `benchmark/mini_tournament_20260313_040331/macduo_BA/match_20260313_040455.log` |
| AntiMacDuoV2 | AB | 0-2-1 | 0.0% | 0.110 | 0.113 | `benchmark/mini_tournament_20260313_040331/v2_AB/match_20260313_040629.log` |
| AntiMacDuoV2 | BA | 0-3-0 | 0.0% | 0.109 | 0.115 | `benchmark/mini_tournament_20260313_040331/v2_BA/match_20260313_040804.log` |

## Aggregated by opponent

| Opponent | Total W-L-D | Win rate | Avg score (candidate) | Avg score (baseline) |
|---|---:|---:|---:|---:|
| AntiMacDuoV2 | 0-5-1 | 0.0% | 0.110 | 0.114 |
| MacDuo | 0-6-0 | 0.0% | 0.024 | 0.931 |

## Verdict on low-HP retreat tweak

- Based on this 12-match sample, performance is **degraded** versus prior expectations if AntiMacDuoV3 should outperform V2 and stay competitive vs MacDuo.
- Vs AntiMacDuoV2 aggregate: 0-5-1 (win rate 0.0%), avg score 0.110 vs 0.114.
- Vs MacDuo aggregate: 0-6-0 (win rate 0.0%), avg score 0.024 vs 0.931.
