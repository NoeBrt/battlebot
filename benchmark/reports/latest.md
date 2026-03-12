# Latest benchmark report

Date: 2026-03-12 11:34 (Europe/Paris)
Branch: `agent/v2`
Commit tested: `037235c`

## Iteration
- Change: added new external strategy family:
  - `algorithms.external.MacDuoUltraMain`
  - `algorithms.external.MacDuoUltraSecondary`
  - shared base `algorithms.external.MacDuoUltraBase`
- Files added:
  - `src/algorithms/external/MacDuoUltraBase.java`
  - `src/algorithms/external/MacDuoUltraMain.java`
  - `src/algorithms/external/MacDuoUltraSecondary.java`

## Tournament protocol
- Runner: sub-agent `bench`
- Format:
  - AB: MacDuo A vs MacDuoUltra B
  - BA: MacDuoUltra A vs MacDuo B
- Matches: 10 per leg (20 total)
- Timeout: 30000 ms
- Delay: 1 ms
- Parameters restored after runs.

## Results
### AB (MacDuo A vs Ultra B, 10 matches)
- W/L/D Team A: **10 / 0 / 0**
- Avg scores: **A=0.746**, **B=0.037**

### BA (Ultra A vs MacDuo B, 10 matches)
- W/L/D Team A: **0 / 10 / 0**
- Avg scores: **A=0.032**, **B=0.701**

### Aggregated (20 matches)
- **MacDuoUltra**: **0W / 20L / 0D** (0%)
- **MacDuo**: **20W / 0L / 0D** (100%)
- Global average scores:
  - Ultra: **0.034**
  - MacDuo: **0.724**

## Verdict
On this 20-match AB+BA benchmark, `MacDuoUltra` is decisively inferior to `MacDuo` and should not be promoted.

## Artifacts
- `logs/mini_tournoi_macduoultra_vs_macduo_20260312_113124/mini_summary.md`
- `logs/mini_tournoi_macduoultra_vs_macduo_20260312_113124/AB_macduo_vs_ultra/match_20260312_113125.log`
- `logs/mini_tournoi_macduoultra_vs_macduo_20260312_113124/BA_ultra_vs_macduo/match_20260312_113734.log`
